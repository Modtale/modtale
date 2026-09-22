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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import net.modtale.launcher.wardrobe.LocalAvatarRenderer;
import net.modtale.launcher.wardrobe.CosmeticCatalogClient;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

/** Native JavaFX wardrobe viewer. Construct and invoke its public methods on the FX thread.
 * The caller owns the executor; dispose cancels this component's work without shutting it down.
 */
public final class WardrobePreview {
    private final Executor executor;
    private final boolean supports3d;
    private final BorderPane root = new BorderPane();
    private final StackPane viewport = new StackPane();
    private final javafx.scene.control.Tooltip interactionHelp = new javafx.scene.control.Tooltip();
    private final Label status = new Label("Select an outfit to preview");
    private final Button retry = new Button("Retry");
    private final Button reset = new Button("Reset");
    private final javafx.scene.control.ComboBox<AnimationChoice> animations = new javafx.scene.control.ComboBox<>();
    private final HBox animationControls = new HBox(6, animations);
    private LocalAvatarRenderer.Rig animationRig;
    private LocalAvatarRenderer.Clip animationClip;
    private AnimationChoice selectedAnimation;
    private boolean viewActions = true;
    private FutureTask<Void> animationPending;
    private long animationGeneration, lastPulse;
    private double animationSeconds, animationSpeed = 1;
    private final javafx.animation.AnimationTimer animationTimer = new javafx.animation.AnimationTimer() {
        @Override public void handle(long now) {
            if (lastPulse != 0) animationSeconds += (now-lastPulse)/1_000_000_000.0 * animationSpeed;
            lastPulse = now;
            if (animationRig != null && animationClip != null) {
                animationRig.apply(animationClip, animationSeconds);
            }
        }
    };
    private record AnimationChoice(CosmeticCatalogClient.AnimationOption option) {
        @Override public String toString() { return option == null ? "Rest pose" :
                option.id().replaceAll("([a-z])([A-Z])", "$1 $2") + (option.kind().equals("EmotesFace") ? " · Face" : ""); }
    }
    private final Rotate yaw = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(0, Rotate.X_AXIS);
    private PerspectiveCamera camera;
    private SubScene scene;
    private FutureTask<Void> pending;
    private java.nio.file.Path localAssets;
    private com.fasterxml.jackson.databind.JsonNode localSkin;
    private long generation;
    private boolean disposed;
    private double dragX, dragY, zoom = 1;
    private CosmeticFraming framing = CosmeticFraming.forCategory("");
    private boolean focusBack;
    private CosmeticFraming headFraming = CosmeticFraming.forCategory("haircut");

    void focusCategory(String category) {
        CosmeticFraming next = CosmeticFraming.forCategory(category);
        if (next.equals(framing)) return;
        framing = next;
        if (!supports3d) cancel();
        focusBack = "cape".equals(category);
        resetView();
    }

    public WardrobePreview(Executor executor) {
        this(executor, Platform.isSupported(ConditionalFeature.SCENE3D));
    }

    WardrobePreview(Executor executor, boolean supports3d) {
        requireFx();
        this.executor = Objects.requireNonNull(executor);
        this.supports3d = supports3d;
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
        status.setWrapText(true);
        status.managedProperty().bind(status.visibleProperty());
        viewport.setAccessibleText("Outfit preview");
        status.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
        status.setMinWidth(0);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setId("wardrobe-preview-status");
        for (Button button : new Button[]{reset, retry}) {
            button.getStyleClass().addAll("btn", "secondary", "small");
        }
        reset.setText(""); reset.setAccessibleText("Reset view");
        reset.setTooltip(new javafx.scene.control.Tooltip("Reset view"));
        reset.setGraphic(net.modtale.launcher.ui.common.LauncherIcons.icon(
                net.modtale.launcher.ui.common.LauncherIcons.Glyph.ROTATE_CCW, 14));
        reset.getStyleClass().add("wardrobe-preview-reset");
        reset.setMinSize(30, 30); reset.setPrefSize(30, 30);
        HBox controls = new HBox(6, animationControls, reset, retry);
        HBox.setHgrow(animationControls, javafx.scene.layout.Priority.ALWAYS);
        controls.setAlignment(Pos.CENTER);
        animations.setId("wardrobe-preview-animation");
        animations.getStyleClass().addAll("select", "wardrobe-preview-motion");
        animations.setStyle("-fx-font-size: 11px;");
        animations.setAccessibleText("Preview animation");
        animations.setMinWidth(0); animations.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(animations, javafx.scene.layout.Priority.ALWAYS);
        animationControls.setAlignment(Pos.CENTER);
        animationControls.setVisible(false);
        animationControls.managedProperty().bind(animationControls.visibleProperty());
        animations.valueProperty().addListener((observable, before, after) -> selectAnimation());
        VBox footer = new VBox(6, status, controls);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(8));
        root.setBottom(footer);
        retry.setOnAction(event -> {
            if (localAssets != null) showLocal(localAssets, localSkin);
        });
        reset.setOnAction(event -> {
            selectedAnimation = null;
            if (!animations.getItems().isEmpty()) animations.getSelectionModel().select(defaultAnimation());
            resetView();
        });
        viewport.setOnMousePressed(event -> { dragX = event.getSceneX(); dragY = event.getSceneY(); });
        viewport.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) return;
            if (scene == null) return;
            yaw.setAngle(yaw.getAngle() + (event.getSceneX()-dragX)*0.6);
            pitch.setAngle(clamp(pitch.getAngle()-(event.getSceneY()-dragY)*0.6, -80, 80));
            dragX = event.getSceneX(); dragY = event.getSceneY();
            event.consume();
        });
        viewport.setOnScroll(event -> {
            if (scene == null) return;
            zoom = clamp(zoom * Math.exp(-event.getDeltaY()*0.002), 0.45, 3);
            fitCamera();
            event.consume();
        });
        viewport.widthProperty().addListener((observable, before, after) -> fitCamera());
        viewport.heightProperty().addListener((observable, before, after) -> fitCamera());
        controls(false, false);
    }

    public Node view() { return root; }

    /** Preview an arbitrary local cosmetic draft without applying it or contacting a rendering service. */
    public void showLocal(java.nio.file.Path assetsZip, com.fasterxml.jackson.databind.JsonNode cosmeticDefinition) {
        requireFx();
        if (disposed) return;
        if (Objects.equals(localAssets, assetsZip) && Objects.equals(localSkin, cosmeticDefinition)
                && (pending != null || scene != null)) return;
        boolean replacing = scene != null && localAssets != null;
        cancel();
        if (!replacing) removeContent();
        localAssets = assetsZip;
        localSkin = cosmeticDefinition == null ? null : cosmeticDefinition.deepCopy();
        long ticket = generation;
        if (!replacing) controls(false, false);
        if (!replacing) {
            showStatus("Loading preview…");
            viewport.getChildren().setAll(WardrobeSkeleton.portrait());
        } else showStatus("Updating preview…");
        var draft = localSkin;
        var localFraming = framing;
        boolean back = focusBack;
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                Group model = net.modtale.launcher.wardrobe.LocalAvatarRenderer.load(assetsZip, draft);
                var fitted = localFraming.fit(model);
                var thumbnail = supports3d ? null : net.modtale.launcher.wardrobe.LocalAvatarThumbnail.render(
                        model, 512, back ? 180 : -20, fitted.centerY(), fitted.scale());
                if (!Thread.currentThread().isInterrupted()) Platform.runLater(() -> {
                    if (!current(ticket)) return;
                    pending = null;
                    try {
                        if (thumbnail != null) {
                            removeContent();
                            var image = new javafx.scene.image.ImageView(thumbnail);
                            image.setPreserveRatio(true);
                            image.fitWidthProperty().bind(viewport.widthProperty());
                            image.fitHeightProperty().bind(viewport.heightProperty());
                            viewport.getChildren().setAll(image);
                            readyStatus("Local outfit preview · Static image", "Rendered locally. Interactive 3D is unavailable on this device.");
                            controls(false, false);
                            return;
                        }
                        double previousYaw = yaw.getAngle(), previousPitch = pitch.getAngle(), previousZoom = zoom;
                        installModel(model);
                        if (replacing) {
                            yaw.setAngle(previousYaw); pitch.setAngle(previousPitch); zoom = previousZoom; fitCamera();
                        }
                        animationRig=LocalAvatarRenderer.rig(model);
                        animations.getItems().add(new AnimationChoice(null));
                        animationRig.animations().forEach(option -> animations.getItems().add(new AnimationChoice(option)));
                        AnimationChoice resume = selectedAnimation;
                        animations.getSelectionModel().select(animations.getItems().stream()
                                .filter(choice -> choice.equals(resume)).findFirst().orElseGet(this::defaultAnimation));
                        animationControls.setVisible(animations.getItems().size()>1);
                        readyStatus("Local outfit preview · Drag to rotate · Scroll to zoom", "Drag to rotate. Scroll to zoom.");
                        controls(true, false);
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
        controls(false, true);
    }

    void showLoading() {
        clear();
        showStatus("Loading preview…");
        viewport.getChildren().setAll(WardrobeSkeleton.portrait());
    }

    public void clear() {
        requireFx();
        cancel();
        localAssets = null; localSkin = null;
        removeContent();
        showStatus("Select an outfit to preview");
        controls(false, false);
    }

    public void dispose() {
        requireFx();
        if (disposed) return;
        clear();
        disposed = true;
        retry.setOnAction(null);
        reset.setOnAction(null);
        viewport.setOnMousePressed(null);
        viewport.setOnMouseDragged(null);
        viewport.setOnMouseReleased(null);
        viewport.setOnScroll(null);
    }

    private void installModel(Group model) {
        removeContent();
        headFraming = CosmeticFraming.forCategory("haircut").fit(model);
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
        controls(true, false);
    }

    private void showStatus(String message) {
        status.setText(message);
        boolean loading = message.startsWith("Loading") || message.startsWith("Updating");
        status.setGraphic(loading ? WardrobeSkeleton.line(110, 10) : null);
        status.setContentDisplay(loading ? javafx.scene.control.ContentDisplay.GRAPHIC_ONLY : javafx.scene.control.ContentDisplay.TEXT_ONLY);
        status.setAccessibleText(message);
        status.setVisible(true);
        javafx.scene.control.Tooltip.uninstall(viewport, interactionHelp);
        viewport.setAccessibleHelp(message);
    }

    private void readyStatus(String state, String help) {
        // Keep a stable state string for diagnostics/tests without reserving footer space.
        status.setText(state);
        status.setGraphic(null);
        status.setAccessibleText(state);
        status.setVisible(false);
        interactionHelp.setText(help);
        javafx.scene.control.Tooltip.uninstall(viewport, interactionHelp);
        javafx.scene.control.Tooltip.install(viewport, interactionHelp);
        viewport.setAccessibleHelp(help);
    }

    private void resetView() {
        yaw.setAngle(focusBack ? 180 : -20);
        pitch.setAngle(0);
        zoom = 1;
        fitCamera();
    }

    private void fitCamera() {
        if (camera == null) return;
        double aspect = Math.max(0.1, viewport.getWidth()/Math.max(1, viewport.getHeight()));
        double halfAngle = Math.atan(Math.tan(Math.toRadians(camera.getFieldOfView()/2))*Math.min(1,aspect));
        var fitted = framing.group().equals("Head") ? headFraming : framing;
        camera.setTranslateY(fitted.centerY());
        camera.setTranslateZ(-1.15/Math.sin(halfAngle)*zoom*fitted.scale());
    }

    private void stopAnimation() {
        animationGeneration++;
        if(animationPending!=null) { animationPending.cancel(true); animationPending=null; }
        animationTimer.stop(); lastPulse=0; animationSeconds=0; animationClip=null;
        if(animationRig!=null)animationRig.reset();
    }

    private AnimationChoice defaultAnimation() {
        return animations.getItems().stream()
                .filter(choice -> choice.option != null && choice.option.id().equalsIgnoreCase("Idle"))
                .findFirst().orElse(animations.getItems().getFirst());
    }

    private void selectAnimation() {
        stopAnimation();
        AnimationChoice choice=animations.getValue();
        if(animationRig==null || choice==null)return;
        selectedAnimation = choice;
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
                var clip=LocalAvatarRenderer.loadAnimation(assets,option.animation(),true);
                if(!Thread.currentThread().isInterrupted())Platform.runLater(() -> {
                    if(disposed || ticket!=animationGeneration)return;
                    animationPending=null; animationClip=clip; animationSpeed=option.speed();
                    animationRig.apply(clip,0); animationTimer.start();
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
        camera = null;
        viewport.getChildren().clear();
    }

    private void cancel() {
        generation++;
        if (pending != null) { pending.cancel(true); pending = null; }
    }

    void hideViewActions() {
        viewActions = false;
        reset.setVisible(false); reset.setManaged(false);
    }

    private boolean current(long ticket) { return !disposed && ticket == generation; }
    private void controls(boolean canReset, boolean canRetry) {
        reset.setVisible(canReset && viewActions); reset.setManaged(canReset && viewActions);
        retry.setVisible(canRetry); retry.setManaged(canRetry);
    }
    private static double clamp(double value, double min, double max) { return Math.max(min,Math.min(max,value)); }
    private static void requireFx() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("WardrobePreview must be used on the FX thread");
    }

}
