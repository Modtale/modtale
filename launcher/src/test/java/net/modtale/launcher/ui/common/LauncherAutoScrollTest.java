package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.ImageCursor;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LauncherAutoScrollTest {
    @BeforeAll
    static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { }
    }

    @Test
    void distanceControlsDirectionSpeedAndDeadZone() {
        assertEquals(0, LauncherAutoScroll.velocity(15));
        assertEquals(0, LauncherAutoScroll.velocity(-8));
        assertEquals(Math.pow(50, 2.2) * 0.008, LauncherAutoScroll.velocity(50), 1e-9);
        assertEquals(-Math.pow(50, 2.2) * 0.008, LauncherAutoScroll.velocity(-50), 1e-9);
        assertEquals(1800, LauncherAutoScroll.velocity(1000));
    }

    @Test
    void middleClickScrollsUntilDismissed() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            Pane content = new Pane();
            content.setPrefSize(400, 1800);
            ScrollPane pane = new ScrollPane(content);
            StackPane root = new StackPane(pane);
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 500, 400));
            stage.show();
            LauncherAutoScroll auto = new LauncherAutoScroll(new LauncherScrollAnimator(), () -> { }, p -> { });
            auto.install(root);
            AtomicInteger contentClicks = new AtomicInteger();
            content.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> contentClicks.incrementAndGet());
            try {
                content.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, MouseButton.MIDDLE, 200));
                content.fireEvent(mouse(MouseEvent.MOUSE_CLICKED, MouseButton.MIDDLE, 200));
                assertEquals(0, contentClicks.get(), "middle-click must not also open the content");
                assertEquals(2, root.getChildren().size(), "the anchor indicator should be visible");
                assertTrue(stage.getScene().getCursor() instanceof ImageCursor,
                        "autoscroll should use its directional browser cursor");
                content.fireEvent(mouse(MouseEvent.MOUSE_MOVED, MouseButton.NONE, 300));
                auto.tick(1_000_000_000L);
                auto.tick(1_050_000_000L);
                assertTrue(pane.getVvalue() > pane.getVmin(), "moving below the anchor must scroll down");

                double scrolled = pane.getVvalue();
                content.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY, 300));
                content.fireEvent(mouse(MouseEvent.MOUSE_CLICKED, MouseButton.PRIMARY, 300));
                assertEquals(0, contentClicks.get(), "the dismissal click must not activate content");
                assertEquals(1, root.getChildren().size(), "dismissing should remove the indicator");
                auto.tick(1_100_000_000L);
                assertEquals(scrolled, pane.getVvalue(), 1e-9);

                content.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, MouseButton.MIDDLE, 200));
                root.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                        false, false, false, false));
                assertEquals(1, root.getChildren().size(), "Escape should cancel autoscroll");
                assertFalse(root.getChildren().stream().anyMatch(child -> child.isMouseTransparent()));

                Button header = new Button("Header");
                root.getChildren().add(header);
                header.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, MouseButton.MIDDLE, 200));
                assertEquals(3, root.getChildren().size(),
                        "middle-click on page chrome should scroll the main page");
            } finally {
                auto.stop();
                stage.close();
            }
            return null;
        });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, MouseButton button, double y) {
        return new MouseEvent(type, 100, y, 100, y, button, 1,
                false, false, false, false,
                button == MouseButton.PRIMARY, button == MouseButton.MIDDLE, false,
                false, false, false, null);
    }
}
