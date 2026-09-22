package net.modtale.launcher.ui.wardrobe;

import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import net.modtale.launcher.wardrobe.CosmeticCatalogClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

class WardrobeCategoryNavigationTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "WARDROBE_ASSETS_ZIP", matches = ".+")
    void pictureCategoriesExpandCollapseAndRememberSelection() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        Path assets = Path.of(System.getenv("WARDROBE_ASSETS_ZIP"));
        var catalog = new CosmeticCatalogClient(assets);
        try (var executor = Executors.newSingleThreadExecutor()) {
            AtomicReference<WardrobeCategoryNavigation> ref = new AtomicReference<>();
            var nav = fx(() -> new WardrobeCategoryNavigation(catalog, assets, category -> ref.get().selectCategory(category)));
            ref.set(nav);
            fx(() -> { nav.setMaxWidth(146); return null; });
            Stage stage = fx(() -> {
                var root = new StackPane(nav); root.setStyle("-fx-background-color: #101b2d; -fx-padding: 12;");
                var scene = new Scene(root, 190, 660);
                scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                var window = new Stage(); window.setScene(scene); window.show();
                nav.selectCategory("haircut");
                return window;
            });
            try {
                fx(() -> {
                    var head = (WardrobeCategoryNavigation.Section) nav.lookup("#wardrobe-group-head");
                    assertTrue(head.isExpanded()); assertTrue(head.isAnimated());
                    var hair = (Button) nav.lookup("#wardrobe-category-haircut");
                    assertNotNull(hair.getGraphic().lookup("ImageView"));
                    assertEquals("", hair.getText()); assertEquals("Hairstyles", hair.getAccessibleText());
                    assertEquals("Hairstyles", hair.getTooltip().getText());
                    ((Button) head.lookup(".wardrobe-category-heading")).fire(); assertFalse(head.isExpanded());
                    var tops = (WardrobeCategoryNavigation.Section) nav.lookup("#wardrobe-group-tops"); tops.setExpanded(true);
                    assertFalse(head.isExpanded()); assertTrue(tops.isExpanded());
                    head.setExpanded(true);
                    assertTrue(hair.getPseudoClassStates().contains(javafx.css.PseudoClass.getPseudoClass("selected")));
                    return null;
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                boolean ready = false;
                while (System.nanoTime() < deadline && !ready) {
                    ready = fx(() -> nav.lookupAll("ImageView").stream().map(ImageView.class::cast).allMatch(image -> image.getImage() != null));
                    if (!ready) Thread.sleep(50);
                }
                assertTrue(ready, "All category models must render");
                fx(() -> {
                    for (var node : nav.lookupAll("ImageView")) {
                        var image = ((ImageView) node).getImage();
                        int visible = 0;
                        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                            int pixel = image.getPixelReader().getArgb(x, y);
                            if (pixel == 0) continue;
                            visible++;
                            assertEquals(160, pixel >>> 24);
                            assertEquals(pixel & 255, (pixel >> 8) & 255);
                            assertEquals(pixel & 255, (pixel >> 16) & 255);
                        }
                        assertTrue(visible > 0);
                    }
                    return null;
                });
                Thread.sleep(250);
                String output = System.getenv("MODTALE_WARDROBE_SCREENSHOTS");
                if (output != null) fx(() -> {
                    var snapshot = nav.snapshot(null, null);
                    var png = new java.awt.image.BufferedImage((int) snapshot.getWidth(), (int) snapshot.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) png.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
                    javax.imageio.ImageIO.write(png, "png", Path.of(output, "wardrobe-category-pictures.png").toFile());
                    return null;
                });
            } finally { fx(() -> { nav.close(); stage.close(); return null; }); }
        }
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(10, TimeUnit.SECONDS);
    }
}
