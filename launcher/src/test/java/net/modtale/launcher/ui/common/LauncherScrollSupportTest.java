package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.css.PseudoClass;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;
import org.junit.jupiter.api.Test;

class LauncherScrollSupportTest {

    @Test
    void verticalOnlyScrollPaneRejectsHorizontalInput() {
        assertEquals(0, LauncherScrollSupport.horizontalDelta(false, 72));
        assertEquals(-72, LauncherScrollSupport.horizontalDelta(true, -72));
    }

    @Test
    void leavesPreciseTouchpadScrollingAndInertiaUntouched() {
        StackPane root = new StackPane();
        StackPane scrollTarget = new StackPane();
        root.getChildren().add(scrollTarget);
        RecordingIdleTimer timer = new RecordingIdleTimer();
        LauncherScrollSupport support = new LauncherScrollSupport(() -> root, timer);
        support.configureNode(scrollTarget);

        ScrollEvent event = pixelScroll(-72, true);
        scrollTarget.fireEvent(event);

        assertFalse(event.isConsumed(), "native scroll events must reach the JavaFX ScrollPane skin");
        assertTrue(Boolean.TRUE.equals(root.getProperties().get(ProjectCardFactory.SCROLL_ACTIVE_PROPERTY)));
        assertTrue(root.getPseudoClassStates().contains(PseudoClass.getPseudoClass("scrolling")),
                "scrolling must suppress transient hover repaints until input becomes idle");
        assertEquals(1, timer.restartCount);
    }

    @Test
    void distinguishesDiscreteWheelInputFromPreciseTouchpadInput() {
        assertTrue(LauncherScrollSupport.isPreciseScroll(pixelScroll(-24, false)));
        assertTrue(LauncherScrollSupport.isPreciseScroll(pixelScroll(-24, true)));
        assertFalse(LauncherScrollSupport.isPreciseScroll(pixelScroll(-40, false)),
                "JavaFX reports a physical Linux wheel notch as an indirect 40px event");
        assertFalse(LauncherScrollSupport.isPreciseScroll(pixelScroll(-20, false), 2),
                "JavaFX reports the same physical notch as 20 logical pixels on a 2x display");
        assertFalse(LauncherScrollSupport.isPreciseScroll(lineScroll(-120)));
    }

    @Test
    void normalizesJavaFxWheelNotchesToTheBrowserDistance() {
        ScrollEvent wheelNotch = pixelScroll(-40, false);
        ScrollEvent touchpadDelta = pixelScroll(-24, false);

        assertEquals(-120, LauncherScrollSupport.browserDelta(wheelNotch, wheelNotch.getDeltaY()));
        assertEquals(-24, LauncherScrollSupport.browserDelta(touchpadDelta, touchpadDelta.getDeltaY()));
        assertEquals(-60, LauncherScrollSupport.browserDelta(pixelScroll(-20, false), -20, 2));
    }

    @Test
    void configuringTwiceDoesNotInstallDuplicateObservers() {
        StackPane root = new StackPane();
        StackPane scrollTarget = new StackPane();
        RecordingIdleTimer timer = new RecordingIdleTimer();
        LauncherScrollSupport support = new LauncherScrollSupport(() -> root, timer);
        support.configureNode(scrollTarget);
        support.configureNode(scrollTarget);

        ScrollEvent event = pixelScroll(-24, false);
        scrollTarget.fireEvent(event);

        assertFalse(event.isConsumed());
        assertEquals(1, timer.restartCount);
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

    private static ScrollEvent lineScroll(double deltaY) {
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
                false,
                0,
                deltaY,
                0,
                deltaY,
                ScrollEvent.HorizontalTextScrollUnits.CHARACTERS,
                0,
                ScrollEvent.VerticalTextScrollUnits.LINES,
                deltaY / 40,
                0,
                null
        );
    }

    private static final class RecordingIdleTimer implements LauncherScrollSupport.InteractionIdleTimer {
        private int restartCount;

        @Override
        public void restart() {
            restartCount++;
        }

        @Override
        public void stop() {
        }
    }
}
