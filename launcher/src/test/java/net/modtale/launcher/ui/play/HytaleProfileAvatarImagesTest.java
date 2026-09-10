package net.modtale.launcher.ui.play;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HytaleProfileAvatarImagesTest {
    @BeforeAll static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { }
    }

    @Test void failedArchiveUsesUsernameAndHidesInitialOnSuccess() throws Exception {
        onFx(() -> {
            var calls = new ArrayList<String>();
            var view = new ImageView();
            var initial = new Label("V");
            var rendered = new WritableImage(16, 16);
            HytaleProfileAvatarImages.load(view, initial, "archive", "username", url -> {
                calls.add(url);
                return url.equals("archive") ? brokenImage() : rendered;
            }, () -> true);
            assertEquals(List.of("archive", "username"), calls);
            assertSame(rendered, view.getImage());
            assertFalse(initial.isVisible());
        });
    }

    @Test void bothFailuresKeepInitialAndDoNotLoop() throws Exception {
        onFx(() -> {
            var calls = new ArrayList<String>();
            var initial = new Label("V");
            HytaleProfileAvatarImages.load(new ImageView(), initial, "archive", "username", url -> {
                calls.add(url);
                return brokenImage();
            }, () -> true);
            assertEquals(List.of("archive", "username"), calls);
            assertTrue(initial.isVisible());
        });
    }

    @Test void obsoleteProfileDoesNotLoadOrReplaceImage() throws Exception {
        onFx(() -> {
            var view = new ImageView(new WritableImage(16, 16));
            Image original = view.getImage();
            HytaleProfileAvatarImages.load(view, new Label("W"), "archive", "username", url -> {
                fail("Detached avatar must not load");
                return null;
            }, () -> false);
            assertSame(original, view.getImage());
        });
    }

    private static Image brokenImage() {
        Image image = new Image(new ByteArrayInputStream(new byte[]{0}));
        assertTrue(image.isError());
        return image;
    }

    private static void onFx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(20, TimeUnit.SECONDS);
    }
}
