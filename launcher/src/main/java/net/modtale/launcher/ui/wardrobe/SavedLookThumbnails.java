package net.modtale.launcher.ui.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.*;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import net.modtale.launcher.wardrobe.LocalAvatarRenderer;

/** Controller-scoped thumbnails of local saved compositions; no account or render-service calls. */
final class SavedLookThumbnails implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "saved-look-thumbnails");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Key, CompletableFuture<Image>> cache = new LinkedHashMap<>(16, .75f, true);
    private boolean closed;
    private record Key(Path assets, JsonNode skin) {}

    // Invoked on the FX thread, including completion and disposal.
    CompletableFuture<Image> load(Path assets, JsonNode skin) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Thumbnail renderer closed"));
        Key key = new Key(assets.toAbsolutePath().normalize(), skin.deepCopy());
        CompletableFuture<Image> existing = cache.get(key);
        if (existing != null) return existing;
        CompletableFuture<Image> result = new CompletableFuture<>();
        cache.put(key, result);
        if (cache.size() > 128) cache.remove(cache.keySet().iterator().next());
        worker.execute(() -> {
            try {
                Group model = LocalAvatarRenderer.load(key.assets(), key.skin());
                Platform.runLater(() -> {
                    if (closed) { result.cancel(false); return; }
                    try { result.complete(snapshot(model)); }
                    catch (Exception ex) { cache.remove(key, result); result.completeExceptionally(ex); }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> { cache.remove(key, result); result.completeExceptionally(ex); });
            }
        });
        return result;
    }

    private static Image snapshot(Group model) throws Exception {
        Bounds bounds = LocalAvatarRenderer.bounds(model);
        double radius = Math.sqrt(bounds.getWidth() * bounds.getWidth()
                + bounds.getHeight() * bounds.getHeight() + bounds.getDepth() * bounds.getDepth()) / 2;
        if (!Double.isFinite(radius) || radius < 1e-8) throw new IllegalArgumentException("Empty outfit");
        Group centered = new Group(model);
        centered.getTransforms().add(new Translate(-bounds.getCenterX(), -bounds.getCenterY(), -bounds.getCenterZ()));
        Group normalized = new Group(centered);
        normalized.getTransforms().add(new Scale(1 / radius, -1 / radius, -1 / radius));
        Group rotated = new Group(normalized);
        rotated.getTransforms().add(new Rotate(-20, Rotate.Y_AXIS));
        SubScene view = new SubScene(new Group(rotated, new AmbientLight(Color.WHITE)), 256, 256, true, SceneAntialiasing.BALANCED);
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(.01); camera.setFarClip(100); camera.setFieldOfView(35);
        camera.setTranslateZ(-1.1 / Math.sin(Math.toRadians(17.5)));
        view.setCamera(camera); view.setFill(Color.TRANSPARENT);
        // An offscreen SubScene still needs a Scene for its camera and lights to be synchronized.
        Group root = new Group(view);
        new Scene(root, 256, 256, true);
        root.applyCss(); root.layout();
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        return view.snapshot(parameters, null);
    }

    @Override public void close() {
        closed = true;
        worker.shutdownNow();
        cache.values().forEach(future -> future.cancel(false));
        cache.clear();
    }
}
