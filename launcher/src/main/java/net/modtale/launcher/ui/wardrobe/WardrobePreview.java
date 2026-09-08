package net.modtale.launcher.ui.wardrobe;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import net.modtale.launcher.platform.SystemBrowser;
import net.modtale.launcher.wardrobe.GltfModelLoader;
import net.modtale.launcher.wardrobe.LocalAvatarRenderer;
import net.modtale.launcher.wardrobe.CosmeticCatalogClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/** Native JavaFX wardrobe viewer. Construct and invoke its public methods on the FX thread.
 * The caller owns the executor; dispose cancels this component's work without shutting it down.
 */
public final class WardrobePreview {
    private final Executor executor;
    private final boolean supports3d;
    private final Fetcher fetcher;
    private final Consumer<URI> browser;
    private final BorderPane root = new BorderPane();
    private final StackPane viewport = new StackPane();
    private final javafx.scene.control.Tooltip interactionHelp = new javafx.scene.control.Tooltip();
    private final Label status = new Label("Select an outfit to preview");
    private final Button rotateLeft = new Button("Rotate left");
    private final Button rotateRight = new Button("Rotate right");
    private final Button retry = new Button("Retry");
    private final Button reset = new Button("Reset");
    private final Button external = new Button("Open 3D");
    private final javafx.scene.control.ComboBox<AnimationChoice> animations = new javafx.scene.control.ComboBox<>();
    private final Button pause = new Button("Pause");
    private final HBox animationControls = new HBox(6, animations, pause);
    private LocalAvatarRenderer.Rig animationRig;
    private LocalAvatarRenderer.Clip animationClip;
    private FutureTask<Void> animationPending;
    private long animationGeneration, lastPulse;
    private double animationSeconds, animationSpeed = 1;
    private boolean animationPaused;
    private final javafx.animation.AnimationTimer animationTimer = new javafx.animation.AnimationTimer() {
        @Override public void handle(long now) {
            if (lastPulse != 0) animationSeconds += (now-lastPulse)/1_000_000_000.0 * animationSpeed;
            lastPulse = now;
            if (animationRig != null && animationClip != null) {
                animationRig.apply(animationClip, animationSeconds);
                if (!animationClip.looping() && animationSeconds >= animationClip.durationSeconds()) {
                    stop(); animationPaused=true; pause.setText("Play");
                }
            }
        }
    };
    private record AnimationChoice(CosmeticCatalogClient.AnimationOption option) {
        @Override public String toString() { return option == null ? "Rest pose" :
                option.id().replaceAll("([a-z])([A-Z])", "$1 $2") + (option.kind().equals("EmotesFace") ? " · Face" : ""); }
    }
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Rotate yaw = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(0, Rotate.X_AXIS);
    private PerspectiveCamera camera;
    private SubScene scene;
    private ImageView image;
    private FutureTask<Void> pending;
    private Request request;
    private java.nio.file.Path localAssets;
    private com.fasterxml.jackson.databind.JsonNode localSkin;
    private long generation;
    private boolean disposed;
    private double dragX, dragY, zoom = 1;
    private double capeAngle = 180;
    private boolean capeDragged;

    public WardrobePreview(Executor executor) {
        this(executor, Platform.isSupported(ConditionalFeature.SCENE3D), WardrobePreview::fetch, uri -> {
            try { SystemBrowser.open(uri); }
            catch (IOException ex) { throw new IllegalStateException("Could not open the system browser", ex); }
        });
    }

    WardrobePreview(Executor executor, boolean supports3d, Fetcher fetcher, Consumer<URI> browser) {
        requireFx();
        this.executor = Objects.requireNonNull(executor);
        this.supports3d = supports3d;
        this.fetcher = Objects.requireNonNull(fetcher);
        this.browser = Objects.requireNonNull(browser);
        root.setId("wardrobe-preview");
        root.setMinSize(180, 240);
        root.setPrefSize(300, 335);
        root.setStyle("-fx-background-color: #101b2d; -fx-background-radius: 12;");
        viewport.setMinSize(0, 0);
        root.setCenter(viewport);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(clip);
        progress.setMaxSize(32,32);
        status.setWrapText(true);
        status.managedProperty().bind(status.visibleProperty());
        viewport.setAccessibleText("Outfit preview");
        status.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        status.setMinWidth(0);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setId("wardrobe-preview-status");
        for (Button button : new Button[]{reset, retry, external, rotateLeft, rotateRight, pause}) {
            button.getStyleClass().addAll("btn", "secondary", "small");
        }
        HBox controls = new HBox(6, reset, retry, external);
        controls.setAlignment(Pos.CENTER);
        HBox rotationControls = new HBox(6, rotateLeft, rotateRight);
        rotationControls.setAlignment(Pos.CENTER);
        rotationControls.visibleProperty().bind(rotateLeft.visibleProperty());
        rotationControls.managedProperty().bind(rotationControls.visibleProperty());
        animations.setId("wardrobe-preview-animation");
        animations.getStyleClass().add("select");
        animations.setStyle("-fx-font-size: 11px;");
        animations.setAccessibleText("Preview animation");
        animations.setMinWidth(0); animations.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(animations, javafx.scene.layout.Priority.ALWAYS);
        pause.setId("wardrobe-preview-pause");
        pause.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        animationControls.setAlignment(Pos.CENTER);
        animationControls.setVisible(false);
        animationControls.managedProperty().bind(animationControls.visibleProperty());
        animations.valueProperty().addListener((observable, before, after) -> selectAnimation());
        pause.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> animationControls.isVisible() && animations.getValue()!=null && animations.getValue().option!=null,
                animationControls.visibleProperty(), animations.valueProperty()));
        pause.managedProperty().bind(pause.visibleProperty());
        pause.setOnAction(event -> {
            if (animationClip == null) return;
            animationPaused = !animationPaused;
            if (animationPaused) animationTimer.stop();
            else {
                if (!animationClip.looping() && animationSeconds >= animationClip.durationSeconds()) animationSeconds=0;
                lastPulse=0; animationTimer.start();
            }
            pause.setText(animationPaused ? "Play" : "Pause");
        });
        VBox footer = new VBox(6, status, rotationControls, animationControls, controls);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(8));
        root.setBottom(footer);
        retry.setOnAction(event -> {
            if (localAssets != null) showLocal(localAssets, localSkin);
            else if (request != null) show(request.username, request.skinId, request.cape);
        });
        reset.setOnAction(event -> {
            if (renderedCape()) { capeAngle = 180; zoom = 1; reloadCape(); } else resetView();
        });
        rotateLeft.setOnAction(event -> rotateCape(-30));
        rotateRight.setOnAction(event -> rotateCape(30));
        external.setOnAction(event -> openExternal());
        viewport.setOnMousePressed(event -> { dragX = event.getSceneX(); dragY = event.getSceneY(); capeDragged = false; });
        viewport.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) return;
            if (renderedCape() && image != null) {
                capeAngle = normalizeAngle(capeAngle + (event.getSceneX()-dragX)*0.6);
                capeDragged |= event.getSceneX() != dragX;
                dragX = event.getSceneX();
                event.consume();
                return;
            }
            if (scene == null) return;
            yaw.setAngle(yaw.getAngle() + (event.getSceneX()-dragX)*0.6);
            pitch.setAngle(clamp(pitch.getAngle()-(event.getSceneY()-dragY)*0.6, -80, 80));
            dragX = event.getSceneX(); dragY = event.getSceneY();
            event.consume();
        });
        viewport.setOnMouseReleased(event -> {
            if (renderedCape() && capeDragged) { capeDragged = false; reloadCape(); event.consume(); }
        });
        viewport.setOnScroll(event -> {
            if (scene == null && !(renderedCape() && image != null)) return;
            zoom = clamp(zoom * Math.exp(-event.getDeltaY()*0.002), 0.45, 3);
            fitCamera();
            if (image != null) { image.setScaleX(zoom); image.setScaleY(zoom); }
            event.consume();
        });
        viewport.widthProperty().addListener((observable, before, after) -> fitCamera());
        viewport.heightProperty().addListener((observable, before, after) -> fitCamera());
        controls(false, false, false);
    }

    public Node view() { return root; }

    public void show(String username, String skinId, String cape) {
        requireFx();
        if (disposed) return;
        cancel();
        localAssets = null; localSkin = null;
        removeContent();
        try {
            Request next = new Request(username, skinId, cape);
            if (!next.equals(request)) { capeAngle = 180; zoom = 1; }
            request = next;
        }
        catch (IllegalArgumentException ex) {
            request = null;
            showStatus(ex.getMessage());
            controls(false, false, false);
            return;
        }
        Request selected = request;
        long ticket = generation;
        int angle = (int)Math.round(capeAngle);
        showStatus("Loading preview…");
        viewport.getChildren().setAll(progress);
        controls(false, false, true);
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                Object result = supports3d && !selected.renderedCape()
                        ? GltfModelLoader.load(fetcher.fetch(selected.glb(), GltfModelLoader.MAX_BYTES))
                        : png(fetcher.fetch(selected.png(angle), 8 * 1024 * 1024));
                if (!Thread.currentThread().isInterrupted()) Platform.runLater(() -> {
                    if (!current(ticket)) return;
                    pending = null;
                    try {
                        if (result instanceof Group model) installModel(model);
                        else installImage((Image)result);
                    } catch (RuntimeException ex) { failed(ticket); }
                });
            } catch (Exception ex) {
                if (!Thread.currentThread().isInterrupted()) Platform.runLater(() -> failed(ticket));
            }
            return null;
        });
        pending = task;
        try {
            // Even a direct/caller-runs executor must never fetch or parse on the FX thread.
            executor.execute(() -> {
                if (Platform.isFxApplicationThread()) Thread.startVirtualThread(task);
                else task.run();
            });
        } catch (RejectedExecutionException ex) { failed(ticket); }
    }

    /** Preview an arbitrary local cosmetic draft without applying it or contacting a rendering service. */
    public void showLocal(java.nio.file.Path assetsZip, com.fasterxml.jackson.databind.JsonNode cosmeticDefinition) {
        requireFx();
        if (disposed) return;
        cancel(); removeContent(); request = null;
        localAssets = assetsZip;
        localSkin = cosmeticDefinition == null ? null : cosmeticDefinition.deepCopy();
        long ticket = generation;
        controls(false, false, false);
        if (!supports3d) {
            showStatus("Preview unavailable on this device.");
            return;
        }
        showStatus("Loading preview…");
        viewport.getChildren().setAll(progress);
        var draft = localSkin;
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                Group model = net.modtale.launcher.wardrobe.LocalAvatarRenderer.load(assetsZip, draft);
                if (!Thread.currentThread().isInterrupted()) Platform.runLater(() -> {
                    if (!current(ticket)) return;
                    pending = null;
                    try {
                        installModel(model);
                        animationRig=LocalAvatarRenderer.rig(model);
                        animations.getItems().add(new AnimationChoice(null));
                        animationRig.animations().forEach(option -> animations.getItems().add(new AnimationChoice(option)));
                        animations.getSelectionModel().selectFirst();
                        animationControls.setVisible(animations.getItems().size()>1);
                        readyStatus("Local outfit preview · Drag to rotate · Scroll to zoom", "Drag to rotate. Scroll to zoom.");
                        controls(true, false, false);
                    } catch (RuntimeException ex) { localFailed(ticket, ex); }
                });
            } catch (Exception ex) {
                if (!Thread.currentThread().isInterrupted()) Platform.runLater(() -> localFailed(ticket, ex));
            }
            return null;
        });
        pending = task;
        try { executor.execute(() -> { if (Platform.isFxApplicationThread()) Thread.startVirtualThread(task); else task.run(); }); }
        catch (RejectedExecutionException ex) { localFailed(ticket, ex); }
    }

    private void localFailed(long ticket, Exception error) {
        if (!current(ticket)) return;
        pending = null; removeContent();
        showStatus("Preview unavailable: " + (error.getMessage() == null ? "Please retry." : error.getMessage()));
        controls(false, true, false);
    }

    public void clear() {
        requireFx();
        cancel();
        request = null;
        localAssets = null; localSkin = null;
        removeContent();
        showStatus("Select an outfit to preview");
        controls(false, false, false);
    }

    public void dispose() {
        requireFx();
        if (disposed) return;
        clear();
        disposed = true;
        retry.setOnAction(null);
        reset.setOnAction(null);
        rotateLeft.setOnAction(null);
        rotateRight.setOnAction(null);
        external.setOnAction(null);
        viewport.setOnMousePressed(null);
        viewport.setOnMouseDragged(null);
        viewport.setOnMouseReleased(null);
        viewport.setOnScroll(null);
    }

    private void installModel(Group model) {
        removeContent();
        Bounds bounds;
        try { bounds = net.modtale.launcher.wardrobe.LocalAvatarRenderer.bounds(model); }
        catch (IOException ex) { throw new IllegalArgumentException(ex.getMessage(), ex); }
        double radius = Math.sqrt(bounds.getWidth()*bounds.getWidth()+bounds.getHeight()*bounds.getHeight()+bounds.getDepth()*bounds.getDepth())/2;
        if (!Double.isFinite(radius) || radius < 1e-8) throw new IllegalArgumentException("Empty model bounds");
        Group centered = new Group(model);
        centered.getTransforms().add(new Translate(-bounds.getCenterX(), -bounds.getCenterY(), -bounds.getCenterZ()));
        Group normalized = new Group(centered);
        // glTF is Y-up with the avatar facing +Z; JavaFX's camera looks toward +Z with Y-down.
        normalized.getTransforms().add(new Scale(1/radius, -1/radius, -1/radius));
        Group rotated = new Group(normalized);
        rotated.getTransforms().addAll(yaw,pitch);
        Group world = new Group(rotated, new AmbientLight(Color.WHITE));
        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.01);
        camera.setFarClip(100);
        camera.setFieldOfView(35);
        scene = new SubScene(world, 380, 380, true, SceneAntialiasing.BALANCED);
        scene.setFill(Color.TRANSPARENT);
        scene.setCamera(camera);
        scene.widthProperty().bind(viewport.widthProperty());
        scene.heightProperty().bind(viewport.heightProperty());
        viewport.getChildren().setAll(scene);
        resetView();
        readyStatus("Live 3D preview · Drag to rotate · Scroll to zoom", "Drag to rotate. Scroll to zoom.");
        controls(true, false, true);
    }

    private void installImage(Image png) {
        removeContent();
        image = new ImageView(png);
        image.setPreserveRatio(true);
        image.setSmooth(true);
        image.fitWidthProperty().bind(viewport.widthProperty());
        image.fitHeightProperty().bind(viewport.heightProperty());
        image.setScaleX(zoom); image.setScaleY(zoom);
        viewport.getChildren().setAll(image);
        if (renderedCape()) {
            readyStatus("Rendered cape preview · Drag to rotate · Scroll to zoom", "Drag and release to rotate the cape. Scroll to zoom.");
            controls(true, false, true);
            return;
        }
        readyStatus("Static PNG preview — native 3D is unavailable on this platform. Open 3D to rotate.", "Static preview. Open 3D in your browser to rotate.");
        controls(false, false, true);
    }

    private void failed(long ticket) {
        if (!current(ticket)) return;
        pending = null;
        removeContent();
        showStatus(renderedCape() ? "Cape preview could not be loaded. Retry or view the render." : "Preview could not be loaded. Retry or open 3D in your browser.");
        controls(false, true, request != null);
    }

    private void openExternal() {
        if (disposed || request == null) return;
        URI uri = renderedCape() ? request.png((int)Math.round(capeAngle)) : request.preview();
        long ticket = generation;
        try {
            executor.execute(() -> {
                Runnable open = () -> {
                    try { browser.accept(uri); }
                    catch (RuntimeException ex) { Platform.runLater(() -> {
                        if (current(ticket)) showStatus("Could not open your browser. Try again.");
                    }); }
                };
                if (Platform.isFxApplicationThread()) Thread.startVirtualThread(open); else open.run();
            });
        } catch (RejectedExecutionException ex) { showStatus("Could not open your browser. Try again."); }
    }

    private void showStatus(String message) {
        status.setText(message);
        status.setVisible(true);
        javafx.scene.control.Tooltip.uninstall(viewport, interactionHelp);
        viewport.setAccessibleHelp(message);
    }

    private void readyStatus(String state, String help) {
        // Keep a stable state string for diagnostics/tests without reserving footer space.
        status.setText(state);
        status.setVisible(false);
        interactionHelp.setText(help);
        javafx.scene.control.Tooltip.uninstall(viewport, interactionHelp);
        javafx.scene.control.Tooltip.install(viewport, interactionHelp);
        viewport.setAccessibleHelp(help);
    }

    private boolean renderedCape() { return request != null && request.renderedCape(); }
    private static double normalizeAngle(double value) { return ((value % 360) + 360) % 360; }
    private void rotateCape(double delta) {
        capeAngle = normalizeAngle(capeAngle + delta);
        reloadCape();
    }
    private void reloadCape() {
        if (renderedCape()) show(request.username, request.skinId, request.cape);
    }

    private void resetView() {
        yaw.setAngle(-20);
        pitch.setAngle(0);
        zoom = 1;
        fitCamera();
    }

    private void fitCamera() {
        if (camera == null) return;
        double aspect = Math.max(0.1, viewport.getWidth()/Math.max(1, viewport.getHeight()));
        double halfAngle = Math.atan(Math.tan(Math.toRadians(camera.getFieldOfView()/2))*Math.min(1,aspect));
        camera.setTranslateZ(-1.15/Math.sin(halfAngle)*zoom);
    }

    private void stopAnimation() {
        animationGeneration++;
        if(animationPending!=null) { animationPending.cancel(true); animationPending=null; }
        animationTimer.stop(); lastPulse=0; animationSeconds=0; animationClip=null;
        animationPaused=false; pause.setText("Pause"); pause.setDisable(true);
        if(animationRig!=null)animationRig.reset();
    }

    private void selectAnimation() {
        stopAnimation();
        AnimationChoice choice=animations.getValue();
        if(animationRig==null || choice==null)return;
        if(choice.option==null) {
            readyStatus("Local outfit preview · Drag to rotate · Scroll to zoom", "Drag to rotate. Scroll to zoom.");
            return;
        }
        long ticket=animationGeneration;
        var assets=localAssets;
        var option=choice.option;
        showStatus("Loading animation…");
        FutureTask<Void> task=new FutureTask<>(() -> {
            try {
                var clip=LocalAvatarRenderer.loadAnimation(assets,option.animation(),option.looping());
                if(!Thread.currentThread().isInterrupted())Platform.runLater(() -> {
                    if(disposed || ticket!=animationGeneration)return;
                    animationPending=null; animationClip=clip; animationSpeed=option.speed();
                    animationRig.apply(clip,0); pause.setDisable(false); animationTimer.start();
                    readyStatus("Local outfit preview · Drag to rotate · Scroll to zoom", "Drag to rotate. Scroll to zoom.");
                });
            } catch(Exception error) {
                if(!Thread.currentThread().isInterrupted())Platform.runLater(() -> {
                    if(!disposed && ticket==animationGeneration) { animationPending=null; showStatus("Animation unavailable. Choose another animation."); }
                });
            }
            return null;
        });
        animationPending=task;
        try { executor.execute(() -> { if(Platform.isFxApplicationThread())Thread.startVirtualThread(task);else task.run(); }); }
        catch(RejectedExecutionException error) { animationPending=null; showStatus("Animation unavailable. Choose another animation."); }
    }

    private void removeContent() {
        stopAnimation(); animationRig=null;
        animations.getItems().clear(); animationControls.setVisible(false);
        if (scene != null) {
            scene.widthProperty().unbind(); scene.heightProperty().unbind();
            scene.setRoot(new Group()); scene.setCamera(null); scene = null;
        }
        if (image != null) {
            image.fitWidthProperty().unbind(); image.fitHeightProperty().unbind(); image.setImage(null); image = null;
        }
        camera = null;
        viewport.getChildren().clear();
    }

    private void cancel() {
        generation++;
        if (pending != null) { pending.cancel(true); pending = null; }
    }

    private boolean current(long ticket) { return !disposed && ticket == generation; }
    private void controls(boolean canReset, boolean canRetry, boolean canOpen) {
        rotateLeft.setVisible(canReset && renderedCape()); rotateLeft.setManaged(canReset && renderedCape());
        rotateRight.setVisible(canReset && renderedCape()); rotateRight.setManaged(canReset && renderedCape());
        external.setText(renderedCape() ? "View render" : "Open 3D");
        reset.setVisible(canReset); reset.setManaged(canReset);
        retry.setVisible(canRetry); retry.setManaged(canRetry);
        external.setVisible(canOpen); external.setManaged(canOpen);
    }
    private static double clamp(double value, double min, double max) { return Math.max(min,Math.min(max,value)); }
    private static void requireFx() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("WardrobePreview must be used on the FX thread");
    }

    @FunctionalInterface interface Fetcher { byte[] fetch(URI uri, int limit) throws IOException; }

    /** Bound both response size and time, close every connection, and never follow redirects to other hosts. */
    static byte[] fetch(URI uri, int limit) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(8_000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "model/gltf-binary, image/png");
        connection.setRequestProperty("User-Agent", "ModtaleLauncher-Wardrobe/1.0");
        long deadline = System.nanoTime() + Duration.ofSeconds(35).toNanos();
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Preview server returned HTTP " + status);
            if (connection.getContentLengthLong() > limit) throw new IOException("Preview exceeds size limit");
            try (var input = connection.getInputStream(); var output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) throw new IOException("Preview request cancelled or timed out");
                    if ((long)output.size()+read > limit) throw new IOException("Preview exceeds size limit");
                    output.write(buffer,0,read);
                }
                return output.toByteArray();
            }
        } finally { connection.disconnect(); }
    }

    private static Image png(byte[] bytes) throws IOException {
        if (bytes.length < 24 || ByteBuffer.wrap(bytes).getLong() != 0x89504e470d0a1a0aL) throw new IOException("Expected a PNG preview");
        int width = ByteBuffer.wrap(bytes).getInt(16), height = ByteBuffer.wrap(bytes).getInt(20);
        if (width <= 0 || height <= 0 || width > 2048 || height > 2048) throw new IOException("Invalid PNG dimensions");
        Image image = new Image(new ByteArrayInputStream(bytes));
        if (image.isError()) throw new IOException("Invalid PNG preview", image.getException());
        return image;
    }

    record Request(String username, String skinId, String cape) {
        Request {
            if (username == null || !username.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Enter a valid Hytale username to preview");
            if ((skinId != null && skinId.length() > 2048) || (cape != null && cape.length() > 2048))
                throw new IllegalArgumentException("Outfit preview parameters are too long");
        }
        URI glb() { return url("glb/", "download=true"); }
        boolean renderedCape() { return (skinId == null || skinId.isBlank()) && cape != null && !cape.isBlank(); }
        URI png() { return png(180); }
        URI png(int angle) { return renderedCape() ? url("cape/", "size=1024&rotate=" + angle) : url("full/", "size=1024&rotate=25"); }
        URI preview() { return url("glb/", "bg=101b2d"); }
        private URI url(String kind, String query) {
            if (skinId != null && !skinId.isBlank()) query += "&skin_id=" + encode(skinId);
            if (cape != null && !cape.isBlank()) query += "&cape=" + encode(cape);
            return URI.create("https://hyvatar.io/render/" + kind + username + "?" + query);
        }
        private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    }
}
