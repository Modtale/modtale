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
                        case "haircut" -> "MagicalPonytail"; case "ears" -> "Elf_Ears_Large_Down";
                        case "mouth" -> "Mouth_Makeup";
                        case "headAccessory" -> "WitchHat"; case "faceAccessory" -> "RoundGlasses";
                        case "earAccessory" -> "EarHoops"; case "facialHair" -> "TwirlyMoustache";
                        case "overtop" -> "PuffyJacket"; case "undertop" -> "Short_Sleeves_Shirt";
                        case "pants" -> "ApprenticePants"; case "overpants" -> "KneePads";
                        case "underwear" -> "Boxer"; case "shoes" -> "HeeledBoots_Popstar"; case "gloves" -> "BoxingGloves";
                        case "cape" -> "Cape_Royal_Emissary"; default -> "";
                    };
                    var choice = options.stream().filter(o -> o.assetId().equals(preferred))
                            .sorted(java.util.Comparator.comparingInt(o -> modelCategory.equals("earAccessory") && o.variantId().equals("Left") ? 0 : 1)).findFirst()
                            .orElse(options.isEmpty() ? null : options.getFirst());
                    var skin = catalog.defaultSkin();
                    boolean portrait = modelCategory.equals("face");
                    for (var entry : CosmeticCatalogClient.categories())
                        if (!entry.key().equals("bodyCharacteristic")
                                && !(portrait && Set.of("face", "ears", "eyes", "eyebrows", "mouth").contains(entry.key()))) skin.putNull(entry.key());
                    if (choice != null) skin.put(modelCategory, choice.id());
                    var model = LocalAvatarRenderer.load(assets, skin, catalog);
                    String expression = switch (modelCategory) {
                        case "mouth" -> "Cheerful"; case "eyebrows" -> "Angry"; default -> "";
                    };
                    if (!expression.isEmpty()) {
                        var pose = catalog.animations("EmotesFace").stream().filter(a -> a.id().equals(expression)).findFirst();
                        if (pose.isPresent()) LocalAvatarRenderer.rig(model).apply(LocalAvatarRenderer.loadAnimation(assets, pose.get().animation(), true), .3);
                    }
                    isolate(model, modelCategory);
                    if (modelCategory.equals("ears")) model.getTransforms().add(new javafx.scene.transform.Rotate(-45, javafx.scene.transform.Rotate.Z_AXIS));
                    double yaw = switch (modelCategory) {
                        case "cape" -> 180; case "earAccessory" -> 65; case "haircut" -> -80;
                        case "shoes", "gloves" -> 80;
                        case "mouth", "ears", "eyebrows", "pants", "overpants", "underwear" -> 0; case "face" -> -25; default -> -20;
                    };
                    Image source = portrait ? LocalAvatarThumbnail.renderSkeleton(model, 256, yaw, 0, 1)
                            : LocalAvatarThumbnail.render(model, 256, yaw, 0, 1);
                    Image image = stylize(source,
                            Set.of("ears", "mouth").contains(modelCategory), portrait);
                    Platform.runLater(() -> { if (closed) result.cancel(false); else result.complete(image); });
                } catch (Exception error) { Platform.runLater(() -> result.completeExceptionally(error)); }
            });
            return result;
        });
    }

    private static Image stylize(Image source, boolean contrast, boolean portrait) {
        var pixels = source.getPixelReader();
        int width = (int) source.getWidth(), height = (int) source.getHeight();
        int limit = width;
        int alphaThreshold = portrait ? 1 : 128;
        int left = limit, right = -1, top = height, bottom = -1;
        for (int y = 0; y < height; y++) for (int x = 0; x < limit; x++) {
            if ((pixels.getArgb(x, y) >>> 24) < alphaThreshold) continue;
            left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
        }
        if (right < left) return source;
        if (portrait) bottom = top + (int) ((bottom - top) * .78);
        double low = 255, high = 0;
        if (contrast) for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
            int argb = pixels.getArgb(x, y);
            if ((argb >>> 24) < alphaThreshold) continue;
            double value = (argb >> 16 & 255) * .2126 + (argb >> 8 & 255) * .7152 + (argb & 255) * .0722;
            low = Math.min(low, value); high = Math.max(high, value);
        }
        int padding = Math.max(3, Math.max(right - left, bottom - top) / 10);
        var image = new javafx.scene.image.WritableImage(right - left + 1 + padding * 2, bottom - top + 1 + padding * 2);
        for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
            int argb = pixels.getArgb(x, y);
            if ((argb >>> 24) < alphaThreshold) continue;
            double luminance = ((argb >> 16 & 255) * .2126 + (argb >> 8 & 255) * .7152 + (argb & 255) * .0722) / 255;
            int grey = contrast && high - low > 4
                    ? 65 + (int) Math.round(Math.clamp((luminance * 255 - low) / (high - low), 0, 1) * 4) * 43
                    : 145 + (int) Math.round(luminance * 3) * 28;
            image.getPixelWriter().setArgb(x - left + padding, y - top + padding, 0xa0000000 | grey << 16 | grey << 8 | grey);
        }
        return image;
    }

    private static void isolate(Node node, String category) {
        if (node instanceof MeshView) {
            String id = Objects.toString(node.getId(), "");
            boolean portrait = category.equals("face") && (id.startsWith("ears:")
                    || id.startsWith("bodyCharacteristic:") && (id.contains(":Head:") || id.contains(":Neck:") || id.contains(":Chest:") || id.contains(":L-Arm:") || id.contains(":R-Arm:")));
            node.setVisible(node.isVisible() && (portrait || !category.equals("face") && id.startsWith(category + ":"))
                    && (!Set.of("ears", "gloves", "shoes").contains(category) || !id.contains(":R-")));
        }
        if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(child -> isolate(child, category));
    }

    @Override public void close() { closed = true; worker.shutdownNow(); cache.values().forEach(f -> f.cancel(false)); cache.clear(); }
}
