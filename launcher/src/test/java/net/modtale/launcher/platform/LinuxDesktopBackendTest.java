package net.modtale.launcher.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class LinuxDesktopBackendTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "GDK_BACKEND", matches = "wayland")
    void nativeWaylandRendersAndResizesWithoutX11() throws Exception {
        assertNull(System.getenv("DISPLAY"), "Wayland validation must not silently use XWayland");
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { }
        FutureTask<Void> task = new FutureTask<>(() -> {
            assertTrue(LinuxDesktopBackend.isWayland(), "Glass must use the Wayland display");
            Stage stage = new Stage();
            try {
                StackPane root = new StackPane(new Rectangle(80, 80, Color.CORNFLOWERBLUE));
                stage.setScene(new Scene(root, 400, 300));
                stage.show();
                for (int size : new int[]{400, 600, 320, 500}) {
                    stage.setWidth(size);
                    stage.setHeight(size);
                    root.applyCss();
                    root.layout();
                    var pixels = root.snapshot(null, null);
                    assertEquals(Color.CORNFLOWERBLUE, pixels.getPixelReader().getColor(
                            (int) pixels.getWidth() / 2, (int) pixels.getHeight() / 2));
                }
            } finally { stage.close(); }
            return null;
        });
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
