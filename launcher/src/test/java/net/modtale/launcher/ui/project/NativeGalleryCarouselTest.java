package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;
import net.modtale.launcher.ui.common.CachedImageLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeGalleryCarouselTest {
    @BeforeAll
    static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException started) { /* Shared toolkit. */ }
    }

    @Test
    void manualPauseRetainsElapsedTimeAndSurvivesVideoNavigation() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            var now = new AtomicLong();
            String asset = getClass().getResource("/net/modtale/launcher/ui/nativefx/assets/project-placeholder.png").toExternalForm();
            var carousel = new NativeGalleryCarousel(new CachedImageLoader(url -> asset, Runnable::run),
                    url -> {}, now::get);
            Node gallery = carousel.render(List.of(
                    new NativeGalleryCarousel.ImageItem(asset, "First", ""),
                    new NativeGalleryCarousel.ImageItem("https://youtu.be/dQw4w9WgXcQ", "Video", ""),
                    new NativeGalleryCarousel.ImageItem(asset, "Third", "")), 0, NativeGalleryCarousel.Variant.INLINE);
            StackPane host = new StackPane(gallery);
            new Scene(host, 900, 700);
            Button playback = (Button) gallery.lookup(".project-gallery-carousel-playback");
            Button next = (Button) gallery.lookup(".next");
            Button previous = (Button) gallery.lookup(".previous");
            Scale scale = (Scale) gallery.lookup(".project-gallery-carousel-progress-fill").getTransforms().getFirst();
            now.set(2_000_000_000L);
            playback.fire();
            assertEquals("Resume slideshow", playback.getAccessibleText());
            assertEquals(0.25, scale.getX());
            now.set(20_000_000_000L);
            playback.fire();
            assertEquals(0.25, scale.getX());
            now.set(22_000_000_000L);
            playback.fire();
            assertEquals(0.5, scale.getX());
            next.fire(); // Video selected while manually paused.
            now.set(100_000_000_000L);
            next.fire();
            assertEquals("Resume slideshow", playback.getAccessibleText());
            assertEquals(0, scale.getX());
            playback.fire();
            previous.fire(); // Video selected while playing: automatic pause.
            now.set(200_000_000_000L);
            host.getChildren().clear();
            host.getChildren().add(gallery);
            assertEquals(0, scale.getX());
            next.fire();
            now.set(202_000_000_000L);
            playback.fire();
            assertEquals(0.25, scale.getX(), "Video time must not consume the following image interval");
            host.getChildren().clear();
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
