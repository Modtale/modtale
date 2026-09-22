package net.modtale.launcher.ui.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.image.Image;
import net.modtale.launcher.wardrobe.LocalAvatarRenderer;

/** Controller-scoped thumbnails of local saved compositions; no account or render-service calls. */
public final class SavedLookThumbnails implements AutoCloseable {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "saved-look-thumbnails");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<Key, CompletableFuture<Image>> cache = new LinkedHashMap<>(16, .75f, true);
    private final Map<String, CompletableFuture<Image>> skeletons = new LinkedHashMap<>();
    private Path skeletonAssets;
    private Group skeletonModel;
    private boolean closed;
    private record Key(Path assets, JsonNode skin, String category) {}
    private Path catalogAssets;
    private net.modtale.launcher.wardrobe.CosmeticCatalogClient catalog;

    // Invoked on the FX thread, including completion and disposal.
    public CompletableFuture<Image> load(Path assets, JsonNode skin) { return load(assets, skin, ""); }

    public CompletableFuture<Image> loadCape(Path assets, String cape) {
        var skin = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("cape", cape);
        return load(assets, skin, "cape");
    }

    public CompletableFuture<Image> load(Path assets, JsonNode skin, String category) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Thumbnail renderer closed"));
        Key key = new Key(assets.toAbsolutePath().normalize(), skin.deepCopy(), category);
        CompletableFuture<Image> existing = cache.get(key);
        if (existing != null) return existing;
        CompletableFuture<Image> result = new CompletableFuture<>();
        cache.put(key, result);
        if (cache.size() > 128) {
            var oldest = cache.entrySet().iterator(); var entry = oldest.next();
            entry.getValue().cancel(false); oldest.remove();
        }
        worker.execute(() -> {
            try {
                if (result.isCancelled()) return;
                if (!key.assets().equals(catalogAssets)) {
                    catalog = new net.modtale.launcher.wardrobe.CosmeticCatalogClient(key.assets());
                    catalogAssets = key.assets();
                }
                JsonNode composition = key.skin();
                if (category.equals("face") && composition.isObject() && composition.isEmpty()) {
                    composition = catalog.defaultSkin();
                }
                if (category.equals("cape") && !composition.has("bodyCharacteristic")) {
                    var baseline = catalog.defaultSkin(); baseline.set("cape", composition.path("cape")); composition = baseline;
                }
                Group model = LocalAvatarRenderer.load(key.assets(), composition, catalog);
                var framing = CosmeticFraming.forCategory(category).fit(model);
                Image image = net.modtale.launcher.wardrobe.LocalAvatarThumbnail.render(model, 256,
                        "cape".equals(category) ? 180 : -20, framing.centerY(), framing.scale());
                Platform.runLater(() -> {
                    if (closed || result.isCancelled()) { result.cancel(false); return; }
                    result.complete(image);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> { cache.remove(key, result); result.completeExceptionally(ex); });
            }
        });
        return result;
    }

    public CompletableFuture<Image> loadSkeleton(Path assets, String category) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Thumbnail renderer closed"));
        Path source = assets.toAbsolutePath().normalize();
        var framing = CosmeticFraming.forCategory(category);
        double yaw = "cape".equals(category) ? 180 : -20;
        String key = source + ":" + framing + ":" + yaw;
        var cached = skeletons.get(key);
        if (cached != null) return cached;
        CompletableFuture<Image> result = new CompletableFuture<>();
        skeletons.put(key, result);
        worker.execute(() -> {
            try {
                if (result.isCancelled()) return;
                if (!source.equals(skeletonAssets)) {
                    var sourceCatalog = new net.modtale.launcher.wardrobe.CosmeticCatalogClient(source);
                    skeletonModel = LocalAvatarRenderer.load(source, sourceCatalog.defaultSkin(), sourceCatalog);
                    skeletonAssets = source;
                }
                var fitted = framing.fit(skeletonModel);
                Image image = net.modtale.launcher.wardrobe.LocalAvatarThumbnail.renderSkeleton(skeletonModel, 256,
                        yaw, fitted.centerY(), fitted.scale());
                Platform.runLater(() -> { if (closed) result.cancel(false); else result.complete(image); });
            } catch (Exception error) {
                Platform.runLater(() -> { skeletons.remove(key, result); result.completeExceptionally(error); });
            }
        });
        return result;
    }

    @Override public void close() {
        closed = true;
        worker.shutdownNow();
        cache.values().forEach(future -> future.cancel(false));
        cache.clear();
        skeletons.values().forEach(future -> future.cancel(false));
        skeletons.clear();
    }
}
