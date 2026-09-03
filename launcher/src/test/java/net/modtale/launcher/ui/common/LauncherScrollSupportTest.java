package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LauncherScrollSupportTest {

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX did not start");
    }

    @Test
    void leavesPlatformPixelScrollingAndInertiaUntouched() throws Exception {
        runOnFx(() -> {
            StackPane root = new StackPane();
            ScrollPane scrollPane = new ScrollPane(new Region());
            root.getChildren().add(scrollPane);
            LauncherScrollSupport support = new LauncherScrollSupport(() -> root);
            support.configure(scrollPane, false);

            ScrollEvent event = pixelScroll(-72, true);
            scrollPane.fireEvent(event);

            assertFalse(event.isConsumed(), "native scroll events must reach the JavaFX ScrollPane skin");
            assertTrue(Boolean.TRUE.equals(root.getProperties().get(ProjectCardFactory.SCROLL_ACTIVE_PROPERTY)));
            assertFalse(root.getPseudoClassStates().contains(PseudoClass.getPseudoClass("scrolling")),
                    "scrolling must not trigger a scene-wide CSS invalidation");
        });
    }

    @Test
    void doesNotGenerateDelayedSyntheticScrollFrames() throws Exception {
        AtomicReference<ScrollPane> scrollPaneReference = new AtomicReference<>();
        AtomicReference<Double> valueAfterInput = new AtomicReference<>();
        runOnFx(() -> {
            Region content = new Region();
            content.resize(800, 4800);
            content.setMinSize(800, 4800);
            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.resize(800, 600);
            new LauncherScrollSupport(StackPane::new).configure(scrollPane, false);
            scrollPane.fireEvent(pixelScroll(-72, false));
            scrollPaneReference.set(scrollPane);
            valueAfterInput.set(scrollPane.getVvalue());
        });

        Thread.sleep(250);

        runOnFx(() -> assertEquals(valueAfterInput.get(), scrollPaneReference.get().getVvalue(), 0.000001,
                "the launcher must not keep mutating scroll position after native input ends"));
    }

    @Test
    void configuringTwiceDoesNotInstallDuplicateObservers() throws Exception {
        runOnFx(() -> {
            StackPane root = new StackPane();
            ScrollPane scrollPane = new ScrollPane(new Region());
            LauncherScrollSupport support = new LauncherScrollSupport(() -> root);
            support.configure(scrollPane, false);
            support.configure(scrollPane, false);

            ScrollEvent event = pixelScroll(-24, false);
            scrollPane.fireEvent(event);

            assertFalse(event.isConsumed());
            assertTrue(Boolean.TRUE.equals(root.getProperties().get(ProjectCardFactory.SCROLL_ACTIVE_PROPERTY)));
        });
    }

    private static ScrollEvent pixelScroll(double deltaY, boolean inertia) {
        return new ScrollEvent(
                ScrollEvent.SCROLL,
                20,
                20,
                20,
                20,
                false,
                false,
                false,
                false,
                false,
                inertia,
                0,
                deltaY,
                0,
                deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE,
                0,
                ScrollEvent.VerticalTextScrollUnits.NONE,
                0,
                0,
                null
        );
    }

    private static void runOnFx(ThrowingRunnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX action timed out");
        if (failure.get() instanceof Exception exception) {
            throw exception;
        }
        if (failure.get() instanceof Error error) {
            throw error;
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
