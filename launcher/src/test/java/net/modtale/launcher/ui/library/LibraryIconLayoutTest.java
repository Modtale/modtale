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
                for (var child : icon.getChildren()) {
                    ImageView image = (ImageView) child;
                    assertEquals(2, image.getBoundsInParent().getMinX(), 0.01);
                    assertEquals(icon.getWidth() - 2, image.getBoundsInParent().getMaxX(), 0.01);
                    assertEquals(2, image.getBoundsInParent().getMinY(), 0.01);
                    assertEquals(icon.getHeight() - 2, image.getBoundsInParent().getMaxY(), 0.01);
                }
            }
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
