package net.modtale.launcher.ui.common;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TooltipHoverTest {
    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task); return task.get(5, TimeUnit.SECONDS);
    }

    @Test void helpUsesMouseTransparentOverlayWithoutOpeningWindowOrChangingSelection() throws Exception {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); } catch (IllegalStateException ignored) {}
        var button = fx(() -> new ToggleButton("Hover target"));
        var tooltip = fx(() -> {
            var t = new Tooltip("Helpful explanation"); t.setShowDelay(Duration.millis(10));
            button.setTooltip(t); button.setSelected(true); return t;
        });
        Stage stage = fx(() -> {
            var root = new StackPane(button);
            var window = new Stage(); window.setScene(new Scene(root, 400, 250));
            LauncherTooltipOverlay.install(window.getScene(), root);
            window.show(); button.requestFocus(); return window;
        });
        try {
            fx(() -> {
                int windows = Window.getWindows().size();
                move(button);
                overlay(stage).show();
                Label help = bubble(stage);
                assertTrue(help.isVisible());
                assertEquals("Helpful explanation", help.getText());
                assertTrue(help.isMouseTransparent());
                assertFalse(help.isManaged());
                assertTrue(button.isSelected());
                assertSame(button, stage.getScene().getFocusOwner());
                assertEquals(windows, Window.getWindows().size());
                assertFalse(tooltip.isShowing(), "Native popup must never open");
                tooltip.setText("Updated explanation");
                assertEquals("Updated explanation", help.getText());
                Event.fireEvent(button, mouse(button, MouseEvent.MOUSE_PRESSED));
                assertFalse(help.isVisible());
                return null;
            });
            // Help attached to a non-control parent remains available for a disabled child.
            fx(() -> {
                button.setDisable(true);
                var parent = (StackPane) stage.getScene().getRoot();
                var disabledHelp = new Tooltip("Unavailable until signed in");
                disabledHelp.setShowDelay(Duration.millis(10));
                LauncherTooltips.install(parent, disabledHelp);
                Event.fireEvent(parent, mouse(parent, MouseEvent.MOUSE_MOVED));
                overlay(stage).show();
                assertEquals("Unavailable until signed in", bubble(stage).getText());
                assertTrue(bubble(stage).isVisible());
                return null;
            });
        } finally { fx(() -> { stage.close(); return null; }); }
    }
    private static LauncherTooltipOverlay overlay(Stage stage) { return (LauncherTooltipOverlay) stage.getScene().getProperties().get(LauncherTooltipOverlay.class); }
    private static Label bubble(Stage stage) { return (Label) stage.getScene().lookup("#launcher-tooltip-overlay"); }
    private static void move(javafx.scene.Node node) { Event.fireEvent(node, mouse(node, MouseEvent.MOUSE_MOVED)); }
    private static MouseEvent mouse(javafx.scene.Node node, javafx.event.EventType<MouseEvent> type) {
        return new MouseEvent(type, 100, 100, 100, 100, javafx.scene.input.MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, true,
                new PickResult(node, 100, 100));
    }
}
