package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherIcons;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LibraryIconLayoutTest {
    @BeforeAll
    static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { /* Shared toolkit. */ }
    }

    @Test
    void modIconMatchesTextOnFirstLayoutAndAfterRowRefresh() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            LibraryWorldRenderer renderer = new LibraryWorldRenderer(
                    null, null, null, null, null, null, null, null, null, null, null, null, null);
            var installed = new net.modtale.launcher.model.install.InstalledProject(
                    "mod", "mod", "Example mod", "PLUGIN", "1.0", "v1", "2026.09",
                    null, null, null, null, null);
            var display = new LibraryWorldProjectDisplay("Example mod", "Example author", "PLUGIN",
                    "", "1.0", "", false, false, false);
            var model = new LibraryWorldProjectModel(installed, null, null, null, false,
                    java.util.List.of(), 0, 0, java.util.List.of(), display);
            var method = LibraryWorldRenderer.class.getDeclaredMethod("projectRow",
                    net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld.class,
                    LibraryWorldProjectModel.class, java.util.List.class);
            method.setAccessible(true);
            StackPane root = new StackPane();
            Scene scene = new Scene(root, 900, 160);
            scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
            root.resize(900, 160);
            for (int refresh = 0; refresh < 3; refresh++) {
                var row = (javafx.scene.Node) method.invoke(renderer, null, model, java.util.List.of());
                root.getChildren().setAll(row);
                root.applyCss();
                for (int pulse = 0; pulse < 5; pulse++) {
                    root.resize(pulse % 2 == 0 ? 900 : 650, 160);
                    row.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), pulse % 2 == 0);
                    root.applyCss();
                    root.layout();
                    var icon = (StackPane) row.lookup(".library-project-icon");
                    var copy = (javafx.scene.layout.VBox) row.lookup(".library-world-project-title").getParent();
                    assertEquals(Math.max(46, copy.prefHeight(-1)), icon.getWidth(), 1,
                            "Icon must start at its final size: refresh=" + refresh + ", pulse=" + pulse);
                    assertEquals(icon.getWidth(), icon.getHeight(), 0.01);
                }
            }
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }

    @Test
    void worldPreviewsCropLandscapeAndPortraitImagesToCenteredSquares() {
        ImageView view = new ImageView();
        LibraryWorldIcon.cropToSquare(view);
        view.setImage(new javafx.scene.image.WritableImage(240, 120));
        assertEquals(new javafx.geometry.Rectangle2D(60, 0, 120, 120), view.getViewport());
        view.setImage(new javafx.scene.image.WritableImage(120, 240));
        assertEquals(new javafx.geometry.Rectangle2D(0, 60, 120, 120), view.getViewport());
    }

    @Test
    void imagesStayInsideBordersAfterCssOverridesIconSize() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            String asset = getClass().getResource("/net/modtale/launcher/ui/nativefx/assets/project-placeholder.png").toExternalForm();
            LibraryWorldRenderer renderer = new LibraryWorldRenderer(
                    new CachedImageLoader(url -> asset, Runnable::run),
                    null, null, null, null, null, null, null, null, null, null, null, null);
            var method = LibraryWorldRenderer.class.getDeclaredMethod("imageIcon", String.class,
                    String.class, LauncherIcons.Glyph.class, double.class, String.class, boolean.class);
            method.setAccessible(true);
            for (String style : new String[] { "library-project-icon", "library-child-icon" }) {
                StackPane icon = (StackPane) method.invoke(renderer, asset, "Mod",
                        LauncherIcons.Glyph.BOX, 46.0, style, true);
                StackPane root = new StackPane(icon);
                Scene scene = new Scene(root, 100, 100);
                scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                root.resize(100, 100);
                root.applyCss();
                root.layout();
                assertEquals(style.equals("library-project-icon") ? 42 : 34, icon.getWidth());
                assertFalse(icon.getChildren().isEmpty());
                double border = style.equals("library-project-icon") ? 4 : 2;
                assertEquals(border, icon.getBorder().getStrokes().getFirst().getWidths().getTop());
                for (var child : icon.getChildren()) {
                    ImageView image = (ImageView) child;
                    assertEquals(border, image.getBoundsInParent().getMinX(), 0.01);
                    assertEquals(icon.getWidth() - border, image.getBoundsInParent().getMaxX(), 0.01);
                    assertEquals(border, image.getBoundsInParent().getMinY(), 0.01);
                    assertEquals(icon.getHeight() - border, image.getBoundsInParent().getMaxY(), 0.01);
                }
            }
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
