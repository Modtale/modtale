package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeInteractionRegressionTest {
    @TempDir Path directory;

    @BeforeAll
    static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { }
    }

    @Test
    void preciseScrollUsesPixelsWithCustomRangesAndStopsAtEdges() throws Exception {
        fx(() -> {
            Region content = new Region();
            content.resize(1200, 2400);
            ScrollPane pane = new ScrollPane(content);
            pane.setViewportBounds(new BoundingBox(0, 0, 400, 400));
            pane.setHmin(-1);
            pane.setHmax(3);
            pane.setHvalue(-1);
            pane.setVmin(10);
            pane.setVmax(20);
            pane.setVvalue(10);
            LauncherScrollSupport support = new LauncherScrollSupport(() -> pane,
                    new LauncherScrollSupport.InteractionIdleTimer() {
                        public void restart() { }
                        public void stop() { }
                    });
            support.configureNode(pane);
            pane.fireEvent(precise(-12.5, -24.5));
            assertEquals(-1 + 4 * 12.5 / 800, pane.getHvalue(), 1e-9);
            assertEquals(10 + 10 * 24.5 / 2000, pane.getVvalue(), 1e-9);
            pane.fireEvent(precise(0, -5000));
            assertEquals(20, pane.getVvalue());
            pane.fireEvent(precise(0, 5000));
            assertEquals(10, pane.getVvalue());
            return null;
        });
    }

    @Test
    void backgroundImageCompletionUpdatesCoverCropAndSharedCache() throws Exception {
        Path file = directory.resolve("wide.png");
        ImageIO.write(new BufferedImage(300, 100, BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
        CachedImageLoader loader = new CachedImageLoader(url -> file.toUri().toString(), Runnable::run, directory);
        CountDownLatch loaded = new CountDownLatch(1);
        ImageView view = fx(() -> {
            ImageView imageView = new ImageView();
            imageView.setFitWidth(100);
            imageView.setFitHeight(100);
            loader.loadInto(imageView, "wide", 300, 100);
            var image = imageView.getImage();
            assertTrue(image.isBackgroundLoading());
            image.progressProperty().addListener(observable -> {
                if (image.getProgress() == 1) loaded.countDown();
            });
            if (image.getProgress() == 1) loaded.countDown();
            return imageView;
        });
        assertTrue(loaded.await(10, TimeUnit.SECONDS));
        fx(() -> {
            assertFalse(view.getImage().isError());
            assertEquals(CachedImageLoader.coverViewport(300, 100, 100, 100), view.getViewport());
            ImageView second = new ImageView();
            loader.loadInto(second, "wide", 300, 100);
            assertSame(view.getImage(), second.getImage());
            view.setFitWidth(200);
            assertEquals(CachedImageLoader.coverViewport(300, 100, 200, 100), view.getViewport());
            return null;
        });
    }

    @Test
    void wheelFramesMatchCapturedChromiumCompositorTraceExactly() throws Exception {
        com.fasterxml.jackson.databind.JsonNode reference;
        try (var input = getClass().getResourceAsStream("/scroll/chromium-153-wheel.json")) {
            reference = new com.fasterxml.jackson.databind.ObjectMapper().readTree(input);
        }
        fx(() -> {
            Region content = new Region();
            content.resize(800, 2400);
            ScrollPane pane = new ScrollPane(content);
            pane.setViewportBounds(new BoundingBox(0, 0, 800, 400));
            double delta = reference.get("deltaPixels").asDouble();
            long frameInterval = Math.round(reference.get("frameIntervalMillis").asDouble() * 1e6);
            long delay = Math.round((200 - reference.get("compositorDurationMillis").asDouble()) * 1e6);
            LauncherScrollAnimator animator = new LauncherScrollAnimator(frameInterval);
            animator.animate(pane, 0, delta, 0);
            for (var frame : reference.get("frames")) {
                long now = delay + Math.round(frame.get("millisAfterFirstFrame").asDouble() * 1e6);
                animator.tick(now);
                assertEquals(frame.get("scrollY").asLong(), Math.round(pane.getVvalue() * 2000),
                        "Rendered CSS pixel at " + frame.get("millisAfterFirstFrame") + " ms");
            }
            animator.cancel(pane);
            return null;
        });
    }

    @Test
    void wheelBurstRetargetsAtThePresentedFrameMinusNativeInputDelay() throws Exception {
        fx(() -> {
            Region content = new Region();
            content.resize(800, 2400);
            ScrollPane pane = new ScrollPane(content);
            pane.setViewportBounds(new BoundingBox(0, 0, 800, 400));
            LauncherScrollAnimator animator = new LauncherScrollAnimator(16_666_667);
            animator.animate(pane, 0, 120, 0);
            animator.tick(0);
            animator.tick(50_000_000);
            double presented = pane.getVvalue();
            animator.animate(pane, LauncherScrollAnimator.metrics(pane), 0, 120,
                    59_000_000, 4_000_000);
            assertEquals(presented, pane.getVvalue(), "Input must not render between animation frames");
            animator.tick(100_000_000);
            assertEquals(LauncherScrollAnimator.wheelTraceOffset(120, 62.666667, 120, 116.666667),
                    pane.getVvalue() * 2000, 1e-6);
            animator.tick(500_000_000);
            assertEquals(240, pane.getVvalue() * 2000, 1e-9);
            animator.cancel(pane);
            return null;
        });
    }

    @Test
    void queuedWheelNotchesAreNotLostWhenTheUiThreadMissesTheirDuration() throws Exception {
        fx(() -> {
            Region content = new Region();
            content.resize(800, 2400);
            ScrollPane pane = new ScrollPane(content);
            pane.setViewportBounds(new BoundingBox(0, 0, 800, 400));
            LauncherScrollAnimator animator = new LauncherScrollAnimator(16_666_667);
            animator.animate(pane, 0, 120, 0);
            animator.animate(pane, 0, 120, 250_000_000);
            assertEquals(0, pane.getVvalue(), "No frame has been presented yet");
            animator.tick(260_000_000);
            animator.tick(600_000_000);
            assertEquals(240, pane.getVvalue() * 2000, 1e-9);
            animator.cancel(pane);
            return null;
        });
    }

    @Test
    void galleryKeepsItsCurrentFrameUntilReplacementHasDecoded() throws Exception {
        Path file = directory.resolve("replacement.png");
        ImageIO.write(new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
        CachedImageLoader loader = new CachedImageLoader(url -> file.toUri().toString(), Runnable::run, directory);
        CountDownLatch replaced = new CountDownLatch(1);
        ImageView view = fx(() -> {
            var original = new javafx.scene.image.WritableImage(1, 1);
            ImageView imageView = new ImageView(original);
            imageView.imageProperty().addListener((observable, previous, current) -> replaced.countDown());
            loader.loadInto(imageView, "replacement", 200, 100, true);
            assertSame(original, imageView.getImage(), "Decoding must not flash a blank gallery frame");
            return imageView;
        });
        assertTrue(replaced.await(10, TimeUnit.SECONDS));
        fx(() -> {
            assertEquals(1, view.getImage().getProgress());
            assertFalse(view.getImage().isError());
            assertEquals(200, view.getImage().getWidth());
            return null;
        });
    }

    private static ScrollEvent precise(double x, double y) {
        return new ScrollEvent(ScrollEvent.SCROLL, 0, 0, 0, 0,
                false, false, false, false, false, true, x, y, x, y,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
