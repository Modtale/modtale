package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class WindowsScrollInputTest {
    @Test
    void animationPolicyReadsTheActualWindowsPreference() {
        var api = Native.load("user32", WindowsScrollInput.User32.class);
        IntByReference expected = new IntByReference();
        assertNotEquals(0, api.SystemParametersInfoW(0x1042, 0, expected, 0));
        assertEquals(expected.getValue() != 0, WindowsScrollInput.animationsEnabled());
    }
    @Test
    void nativeWindowsWheelMessagesReachTheChromiumDistanceOnBothAxes() throws Exception {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { }
        Fixture fixture = fx(() -> {
            Region content = new Region(); content.setPrefSize(2400, 4000);
            ScrollPane pane = new ScrollPane(content);
            Stage stage = new Stage();
            stage.setTitle("Modtale native wheel regression");
            stage.setScene(new Scene(pane, 800, 600)); stage.show();
            pane.applyCss(); pane.layout();
            new LauncherScrollSupport(() -> pane).configure(pane, true);
            return new Fixture(stage, pane);
        });
        try {
            MessageApi api = Native.load("user32", MessageApi.class);
            Pointer window = api.FindWindowW(null, new WString("Modtale native wheel regression"));
            assertNotNull(window, "The test must reach the actual Glass window");
            for (boolean horizontal : new boolean[]{false, true}) {
                for (int wheelDelta : new int[]{120, -30}) {
                    CountDownLatch delivered = new CountDownLatch(1);
                    double[] expected = new double[1];
                    fx(() -> {
                        ScrollPane pane = fixture.pane;
                        pane.setHvalue(.5); pane.setVvalue(.5);
                        var listener = new javafx.event.EventHandler<ScrollEvent>() {
                            @Override public void handle(ScrollEvent event) {
                                assertEquals(horizontal ? ScrollEvent.HorizontalTextScrollUnits.CHARACTERS
                                                : ScrollEvent.VerticalTextScrollUnits.LINES,
                                        horizontal ? event.getTextDeltaXUnits() : event.getTextDeltaYUnits());
                                double text = horizontal ? event.getTextDeltaX() : event.getTextDeltaY();
                                expected[0] = -text * (100.0 / 3);
                                pane.removeEventFilter(ScrollEvent.SCROLL, this);
                                delivered.countDown();
                            }
                        };
                        pane.addEventFilter(ScrollEvent.SCROLL, listener);
                        var point = pane.localToScreen(200, 200);
                        long location = ((long) Math.round(point.getY()) & 0xffff) << 16
                                | ((long) Math.round(point.getX()) & 0xffff);
                        assertNotEquals(0, api.PostMessageW(window, horizontal ? 0x020e : 0x020a,
                                Pointer.createConstant(((long) wheelDelta & 0xffff) << 16),
                                Pointer.createConstant(location)));
                        return null;
                    });
                    assertTrue(delivered.await(5, TimeUnit.SECONDS), "Native wheel message was not delivered");
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    double actual;
                    do {
                        actual = fx(() -> {
                            var metrics = LauncherScrollAnimator.metrics(fixture.pane);
                            return horizontal ? (fixture.pane.getHvalue() - .5) * metrics.maxX()
                                    : (fixture.pane.getVvalue() - .5) * metrics.maxY();
                        });
                        if (Math.abs(actual - expected[0]) < 1e-6) break;
                        Thread.sleep(10);
                    } while (System.nanoTime() < deadline);
                    assertEquals(expected[0], actual, 1e-6, "Native line settings must determine the final distance");
                }
            }
        } finally {
            fx(() -> { fixture.stage.close(); return null; });
        }
    }

    private record Fixture(Stage stage, ScrollPane pane) {}

    interface MessageApi extends StdCallLibrary {
        Pointer FindWindowW(WString className, WString title);
        int PostMessageW(Pointer window, int message, Pointer wParam, Pointer lParam);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }

}
