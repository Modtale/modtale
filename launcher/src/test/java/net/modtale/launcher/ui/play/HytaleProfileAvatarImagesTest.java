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

    @Test void characterRenderReplacesPlaceholderAndPreventsLatePlaceholderOverwrite() throws Exception {
        var skin = new java.util.concurrent.CompletableFuture<com.fasterxml.jackson.databind.JsonNode>();
        var rendered = new java.util.concurrent.CompletableFuture<Image>();
        var definition = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .put("bodyCharacteristic", "Muscular.01");
        var view = new ImageView();
        var initial = new Label("F");
        var face = new WritableImage(16, 16);
        onFx(() -> HytaleProfileAvatarImages.render(view, initial, skin, actual -> {
            assertEquals(definition, actual);
            assertTrue(Platform.isFxApplicationThread());
            return rendered;
        }, () -> true));
        skin.complete(definition);
        onFx(() -> {});
        rendered.complete(face);
        onFx(() -> {
            assertSame(face, view.getImage());
            assertEquals("local-outfit", view.getUserData());
            assertFalse(initial.isVisible());
        });
    }

    @Test void characterRenderCannotReplaceAnObsoleteProfile() throws Exception {
        var current = new java.util.concurrent.atomic.AtomicBoolean(true);
        var rendered = new java.util.concurrent.CompletableFuture<Image>();
        var view = new ImageView(new WritableImage(16, 16));
        Image original = view.getImage();
        onFx(() -> HytaleProfileAvatarImages.render(view, new Label("F"),
                java.util.concurrent.CompletableFuture.completedFuture(
                        new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()),
                skin -> rendered, current::get));
        onFx(() -> current.set(false));
        rendered.complete(new WritableImage(16, 16));
        onFx(() -> {
            assertSame(original, view.getImage());
            assertNull(view.getUserData());
        });
    }

    @Test void unavailableFriendSkinKeepsPlaceholder() throws Exception {
        var view = new ImageView(new WritableImage(16, 16));
        Image original = view.getImage();
        onFx(() -> HytaleProfileAvatarImages.render(view, new Label("F"),
                java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Unavailable")),
                skin -> { throw new AssertionError("Unavailable skins must not render a default character"); },
                () -> true));
        onFx(() -> assertSame(original, view.getImage()));
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
