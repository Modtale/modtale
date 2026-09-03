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
    void leavesPlatformPixelScrollingAndInertiaUntouched() {
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
