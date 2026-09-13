package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void modIconsStayAtTheSameLargerSizeOnFirstLayoutAndAfterRowRefresh() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            String asset = getClass().getResource(
                    "/net/modtale/launcher/ui/nativefx/assets/project-placeholder.png").toExternalForm();
            LibraryWorldRenderer renderer = new LibraryWorldRenderer(
                    new CachedImageLoader(url -> asset, Runnable::run),
                    null, null, null, null, null, null, null, null, null, null, null, null);
            var installed = new net.modtale.launcher.model.install.InstalledProject(
                    "mod", "mod", "Example mod", "PLUGIN", "1.0", "v1", "2026.09",
                    null, null, null, null, null);
            var method = LibraryWorldRenderer.class.getDeclaredMethod("projectRow",
                    net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld.class,
                    LibraryWorldProjectModel.class, java.util.List.class);
            method.setAccessible(true);
            StackPane root = new StackPane();
            Scene scene = new Scene(root, 900, 160);
            scene.getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
            root.resize(900, 160);
            for (int refresh = 0; refresh < 3; refresh++) {
                var display = new LibraryWorldProjectDisplay("Example mod",
                        refresh == 0 ? "" : "Example author", "PLUGIN",
                        refresh == 0 ? "" : asset, "1.0", "", false, false, false);
                var model = new LibraryWorldProjectModel(installed, null, null, null, false,
                        java.util.List.of(), 0, 0, java.util.List.of(), display);
                var row = (javafx.scene.Node) method.invoke(renderer, null, model, java.util.List.of());
                root.getChildren().setAll(row);
                root.applyCss();
                for (int pulse = 0; pulse < 5; pulse++) {
                    root.resize(pulse % 2 == 0 ? 900 : 650, 160);
                    row.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("hover"), pulse % 2 == 0);
                    root.applyCss();
                    root.layout();
                    var icon = (StackPane) row.lookup(".library-project-icon");
                    assertEquals(80, icon.getWidth(), 0.01,
                            "Icon must start at its final size: refresh=" + refresh + ", pulse=" + pulse);
                    assertEquals(icon.getWidth(), icon.getHeight(), 0.01);
                    for (var child : icon.getChildren()) {
                        ImageView image = (ImageView) child;
                        assertEquals(72, image.getFitWidth(), 0.01);
                        assertEquals(72, image.getFitHeight(), 0.01);
                        org.junit.jupiter.api.Assertions.assertTrue(image.isPreserveRatio());
                    }
                }
            }
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }

    @Test
    void loadedTransparentProjectIconsRenderWithoutPlaceholderOrBackground() throws Exception {
        var file = java.nio.file.Files.createTempFile("transparent-project-icon-", ".png");
        try {
            var source = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            source.setRGB(8, 3, 0xffff0000);
            javax.imageio.ImageIO.write(source, "png", file.toFile());
            FutureTask<StackPane[]> task = new FutureTask<>(() -> {
                String asset = file.toUri().toString();
                String placeholder = getClass().getResource(
                        "/net/modtale/launcher/ui/nativefx/assets/project-placeholder.png").toExternalForm();
                java.util.function.Function<String, String> resolver = url -> url == null ? placeholder : url;
                var renderer = new LibraryWorldRenderer(new CachedImageLoader(resolver, Runnable::run),
                        null, null, null, null, null, null, null, null, null, null, null, null);
                var method = LibraryWorldRenderer.class.getDeclaredMethod("imageIcon", String.class,
                        String.class, LauncherIcons.Glyph.class, double.class, String.class, boolean.class);
                method.setAccessible(true);
                var library = (StackPane) method.invoke(renderer, asset, "Transparent",
                        LauncherIcons.Glyph.BOX, 80.0, "library-project-icon", true);
                var project = new net.modtale.launcher.model.project.ProjectSummary(
                        "id", "slug", "Transparent", "", "", "", asset, "", "PLUGIN", 0, 0, "", java.util.List.of());
                var browse = new net.modtale.launcher.ui.browse.card.ProjectCardMedia(resolver, Runnable::run)
                        .projectIcon(project, 80, 4);
                return new StackPane[] {library, browse};
            });
            Platform.runLater(task);
            StackPane[] icons = task.get(30, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean decoded = false;
            do {
                FutureTask<Boolean> ready = new FutureTask<>(() -> java.util.Arrays.stream(icons).allMatch(icon ->
                        imageViews(icon).stream().anyMatch(view -> view.getImage() != null
                                && view.getImage().getUrl() != null
                                && view.getImage().getUrl().endsWith(file.getFileName().toString())
                                && view.getImage().getProgress() == 1 && !view.getImage().isError())));
                Platform.runLater(ready);
                decoded = ready.get(5, TimeUnit.SECONDS);
                if (!decoded) Thread.sleep(10);
            } while (!decoded && System.nanoTime() < deadline);
            assertTrue(decoded, "Snapshots must wait for the actual source image to finish decoding");
            FutureTask<Void> snapshots = new FutureTask<>(() -> {
                for (StackPane icon : icons) {
                    StackPane root = new StackPane(icon);
                    Scene scene = new Scene(root, 100, 100);
                    scene.getStylesheets().add(getClass().getResource(
                            "/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
                    root.resize(100, 100);
                    root.applyCss();
                    root.layout();
                    icon.setEffect(null);
                    var parameters = new javafx.scene.SnapshotParameters();
                    parameters.setFill(javafx.scene.paint.Color.TRANSPARENT);
                    var pixels = icon.snapshot(parameters, null);
                    assertEquals(0, pixels.getPixelReader().getArgb(
                            (int) pixels.getWidth() / 2, (int) pixels.getHeight() / 2),
                            "Transparent source pixels must remain transparent through the styled renderer");
                    boolean hasSourcePixel = false;
                    for (int y = 0; y < pixels.getHeight(); y++) {
                        for (int x = 0; x < pixels.getWidth(); x++) {
                            hasSourcePixel |= pixels.getPixelReader().getArgb(x, y) == 0xffff0000;
                        }
                    }
                    assertTrue(hasSourcePixel, "The rendered icon must contain the decoded source, not a blank image");
                }
                return null;
            });
            Platform.runLater(snapshots);
            snapshots.get(30, TimeUnit.SECONDS);
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    private static java.util.List<ImageView> imageViews(javafx.scene.Node node) {
        var views = new java.util.ArrayList<ImageView>();
        if (node instanceof ImageView image) views.add(image);
        if (node instanceof javafx.scene.Parent parent) {
            for (var child : parent.getChildrenUnmodifiable()) views.addAll(imageViews(child));
        }
        return views;
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
