package net.modtale.launcher.ui.wardrobe;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.image.Image;
import net.modtale.launcher.wardrobe.CosmeticCatalogClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class SavedLookThumbnailsTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    @Test
    @EnabledIfEnvironmentVariable(named = "WARDROBE_ASSETS_ZIP", matches = ".+")
    void rendersSavedCompositionAndReusesThumbnail() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(started::countDown); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        Path assets = Path.of(System.getenv("WARDROBE_ASSETS_ZIP"));
        var skin = new CosmeticCatalogClient(assets).defaultSkin();
        SavedLookThumbnails thumbnails = fx(SavedLookThumbnails::new);
        try {
            var first = fx(() -> thumbnails.load(assets, skin));
            assertSame(first, fx(() -> thumbnails.load(assets, skin.deepCopy())));
            Image image = first.get(30, TimeUnit.SECONDS);
            int visible = 0;
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++)
                if ((image.getPixelReader().getArgb(x, y) >>> 24) > 0) visible++;
            assertTrue(visible > 2000 && visible < 50000, "Thumbnail must contain an avatar on a transparent background: " + visible);
            String output = System.getenv("MODTALE_WARDROBE_SCREENSHOTS");
            if (output != null && !output.isBlank()) {
                var png = new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                javax.imageio.ImageIO.write(png, "png", Path.of(output, "launcher-saved-look-thumbnail.png").toFile());
            }
            skin.put("underwear", "Suit.Blue");
            Image changed = fx(() -> thumbnails.load(assets, skin)).get(30, TimeUnit.SECONDS);
            int differences = 0;
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++)
                if (image.getPixelReader().getArgb(x, y) != changed.getPixelReader().getArgb(x, y)) differences++;
            assertTrue(differences > 100, "Different saved compositions must produce different previews");
            verifySavedCards(assets, skin);
        } finally { fx(() -> { thumbnails.close(); return null; }); }
    }

    private void verifySavedCards(Path assets, com.fasterxml.jackson.databind.JsonNode skin) throws Exception {
        var store = new net.modtale.launcher.wardrobe.WardrobeStore(directory.resolve("looks"));
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().set("skin", skin);
        store.saveItem(new net.modtale.launcher.wardrobe.WardrobeItem(java.util.UUID.randomUUID(),
                net.modtale.launcher.wardrobe.WardrobeItem.Kind.SKIN, "My look", false, "Custom outfits", payload.toString()));
        var settings = new net.modtale.launcher.settings.LauncherSettings();
        settings.setHytaleGamePath(assets.getParent().toString());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var controller = fx(() -> new LauncherWardrobeController(
                    new net.modtale.launcher.wardrobe.WardrobeApiClient(new net.modtale.launcher.hytale.HytaleAuthService(null, null)),
                    store, () -> settings,
                    new net.modtale.launcher.ui.feedback.LauncherFeedback(executor, new javafx.scene.control.Label(),
                            new javafx.scene.layout.VBox(), new javafx.scene.layout.StackPane(), new javafx.scene.control.Label(),
                            new javafx.scene.control.Label(), () -> "Ready"), executor));
            var stage = fx(() -> {
                Platform.setImplicitExit(false);
                var root = new javafx.scene.layout.StackPane(controller.view());
                root.setStyle("-fx-background-color: #0B1120; -fx-padding: 24;");
                var scene = new javafx.scene.Scene(root, 1200, 850);
                scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                var window = new javafx.stage.Stage(); window.setScene(scene); window.show();
                controller.view().lookupAll(".toggle-button").stream()
                        .map(node -> (javafx.scene.control.ToggleButton) node).filter(button -> button.getText().equals("Saved looks"))
                        .findFirst().orElseThrow().fire();
                return window;
            });
            try {
                boolean ready = false;
                for (int i = 0; i < 100 && !ready; i++) {
                    ready = fx(() -> {
                        var card = controller.view().lookup(".wardrobe-card");
                        if (card == null) return false;
                        var image = (javafx.scene.image.ImageView) card.lookup("ImageView");
                        return image != null && image.getImage() != null && !card.lookup(".wardrobe-card-fallback").isVisible();
                    });
                    if (!ready) Thread.sleep(100);
                }
                assertTrue(ready, "Saved card must replace SKIN placeholder with its rendered outfit");
                String output = System.getenv("MODTALE_WARDROBE_SCREENSHOTS");
                if (output != null && !output.isBlank()) {
                    var image = fx(() -> stage.getScene().snapshot(null));
                    int width = (int) image.getWidth(), height = (int) image.getHeight();
                    var png = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                    javax.imageio.ImageIO.write(png, "png", Path.of(output, "launcher-saved-look-card.png").toFile());
                }
            } finally { fx(() -> { controller.close(); stage.close(); return null; }); }
        }
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
