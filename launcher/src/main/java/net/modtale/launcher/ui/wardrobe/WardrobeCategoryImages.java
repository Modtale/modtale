package net.modtale.launcher.ui.wardrobe;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.shape.MeshView;
import net.modtale.launcher.wardrobe.*;

final class WardrobeCategoryImages implements AutoCloseable {
    private final CosmeticCatalogClient catalog;
    private final Path assets;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "wardrobe-category-icons"); thread.setDaemon(true); return thread;
    });
    private final Map<String, CompletableFuture<Image>> cache = new HashMap<>();
    private boolean closed;

    WardrobeCategoryImages(CosmeticCatalogClient catalog, Path assets) { this.catalog = catalog; this.assets = assets; }

    CompletableFuture<Image> load(String category) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Category icons closed"));
        return cache.computeIfAbsent(category, key -> {
            CompletableFuture<Image> result = new CompletableFuture<>();
            worker.execute(() -> {
                try {
                    String modelCategory = key.equals("skinFeature") ? "face" : key;
                    var options = new ArrayList<CosmeticOption>();
                    int page = 1;
                    CosmeticCatalogClient.Page batch;
                    do {
                        batch = catalog.browseAssets(modelCategory, "", page++, 100);
                        options.addAll(batch.options());
                    } while (batch.hasNext());
                    String preferred = switch (modelCategory) {
                        case "face" -> key.equals("skinFeature") ? "Face_Neutral_Freckles" : "Face_Neutral";
                        case "haircut" -> "Quiff"; case "ears" -> "Elf_Ears";
                        case "headAccessory" -> "WitchHat"; case "faceAccessory" -> "RoundGlasses";
                        case "earAccessory" -> "EarHoops"; case "facialHair" -> "TwirlyMoustache";
                        case "overtop" -> "PuffyJacket"; case "undertop" -> "Short_Sleeves_Shirt";
                        case "pants" -> "ApprenticePants"; case "overpants" -> "KneePads";
                        case "shoes" -> "BasicBoots"; case "gloves" -> "BoxingGloves";
                        case "cape" -> "Cape_Royal_Emissary"; default -> "";
                    };
                    var choice = options.stream().filter(o -> o.assetId().equals(preferred))
                            .sorted(java.util.Comparator.comparingInt(o -> modelCategory.equals("earAccessory") && o.variantId().equals("Left") ? 0 : 1)).findFirst()
                            .orElse(options.isEmpty() ? null : options.getFirst());
                    var skin = catalog.defaultSkin();
                    for (var entry : CosmeticCatalogClient.categories())
                        if (!entry.key().equals("bodyCharacteristic")) skin.putNull(entry.key());
                    if (choice != null) skin.put(modelCategory, choice.id());
                    var model = LocalAvatarRenderer.load(assets, skin, catalog);
                    isolate(model, modelCategory);
                    double yaw = switch (modelCategory) { case "cape" -> 180; case "ears", "earAccessory" -> 65; default -> -20; };
                    Image image = stylize(LocalAvatarThumbnail.render(model, 256, yaw, 0, 1),
                            modelCategory.equals("gloves"));
                    Platform.runLater(() -> { if (closed) result.cancel(false); else result.complete(image); });
                } catch (Exception error) { Platform.runLater(() -> result.completeExceptionally(error)); }
            });
            return result;
        });
    }

    private static Image stylize(Image source, boolean singleSide) {
        var pixels = source.getPixelReader();
        int width = (int) source.getWidth(), height = (int) source.getHeight();
        int limit = singleSide ? width / 2 : width;
        int left = limit, right = -1, top = height, bottom = -1;
        for (int y = 0; y < height; y++) for (int x = 0; x < limit; x++) {
            if ((pixels.getArgb(x, y) >>> 24) < 128) continue;
            left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        if (right < left) return source;
        int padding = Math.max(3, Math.max(right - left, bottom - top) / 10);
        var image = new javafx.scene.image.WritableImage(right - left + 1 + padding * 2, bottom - top + 1 + padding * 2);
        for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
            int argb = pixels.getArgb(x, y);
            if ((argb >>> 24) < 128) continue;
            double luminance = ((argb >> 16 & 255) * .2126 + (argb >> 8 & 255) * .7152 + (argb & 255) * .0722) / 255;
            int grey = 145 + (int) Math.round(luminance * 3) * 28;
            image.getPixelWriter().setArgb(x - left + padding, y - top + padding, 0xa0000000 | grey << 16 | grey << 8 | grey);
        }
        return image;
    }

    private static void isolate(Node node, String category) {
        if (node instanceof MeshView) node.setVisible(node.isVisible() && node.getId() != null && node.getId().startsWith(category + ":")
                && (!category.equals("ears") || !node.getId().contains(":R-")));
        if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(child -> isolate(child, category));
    }

    @Override public void close() { closed = true; worker.shutdownNow(); cache.values().forEach(f -> f.cancel(false)); cache.clear(); }
}
