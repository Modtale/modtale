package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Exercises the GTK event queue, including the smooth deltas Glass currently drops. */
class LinuxScrollInputTest {
    @BeforeAll
    static void toolkit() {
        assumeTrue(System.getProperty("os.name").contains("Linux"));
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { }
    }

    @Test
    void gtkTouchpadKeepsFractionalDeltasAndDirectionChangesWithoutWheelAnimation() throws Exception {
        exercise(6, true);
    }

    @Test
    void gtkSmoothWheelRetainsAnimationEvenForFractionalNotches() throws Exception {
        exercise(0, false);
    }

    private void exercise(int source, boolean precise) throws Exception {
        Fixture fixture = fx(() -> {
            Region content = new Region();
            content.setPrefSize(700, 5000);
            ScrollPane pane = new ScrollPane(content);
            Stage stage = new Stage();
            stage.setScene(new Scene(pane, 800, 600));
            stage.show();
            pane.applyCss();
            pane.layout();
            new LauncherScrollSupport(() -> pane).configure(pane, true);
            return new Fixture(stage, pane);
        });
        try {
            // Includes a whole wheel-sized delta, fractional tail, and reversal.
            for (double delta : new double[]{.25, 1, .0625, -.125, -.25}) {
                CountDownLatch delivered = new CountDownLatch(1);
                double[] observed = new double[2];
                fx(() -> {
                    ScrollPane pane = fixture.pane;
                    double range = LauncherScrollAnimator.metrics(pane).maxY();
                    pane.setVvalue(.5);
                    var listener = new javafx.event.EventHandler<ScrollEvent>() {
                        @Override public void handle(ScrollEvent event) {
                            // This assertion covers the actual GTK -> Glass -> JavaFX path.
                            observed[0] = event.getDeltaY();
                            observed[1] = (pane.getVvalue() - .5) * range;
                            pane.removeEventFilter(ScrollEvent.SCROLL, this);
                            delivered.countDown();
                        }
                    };
                    pane.addEventFilter(ScrollEvent.SCROLL, listener);
                    enqueueSmooth(source, delta);
                    return null;
                });
                assertTrue(delivered.await(5, TimeUnit.SECONDS), "GTK event must reach the scene");
                assertEquals(0, observed[0], "Pinned Glass drops raw smooth deltas");
                double scale = fx(() -> LauncherScrollAnimator.outputScale(fixture.pane));
                assertEquals(precise ? delta * 120 / scale : 0, observed[1], 1e-7,
                        precise ? "Touchpad movement must apply immediately" : "Wheel input waits for an animation frame");
            }
        } finally {
            fx(() -> { fixture.stage.close(); return null; });
        }
    }

    private static void enqueueSmooth(int source, double delta) {
        Gdk gdk = Native.load("gdk-3", Gdk.class);
        NativeLibrary objects = NativeLibrary.getInstance("gobject-2.0");
        Pointer display = gdk.gdk_display_get_default();
        Pointer seat = gdk.gdk_display_get_default_seat(display);
        Pointer pointer = seat == null ? null : gdk.gdk_seat_get_pointer(seat);
        NativeLong deviceType = pointer == null ? gdk.gdk_wayland_device_get_type()
                : pointer.getPointer(0).getNativeLong(0);
        Pointer device = objects.getFunction("g_object_new").invokePointer(new Object[]{
                deviceType,
                "name", "Launcher scroll regression device", "input-source", source,
                "display", display, "device-manager", gdk.gdk_display_get_device_manager(display), null});
        Pointer windows = gdk.gdk_screen_get_toplevel_windows(gdk.gdk_screen_get_default());
        try {
            for (Pointer link = windows; link != null; link = link.getPointer(Native.POINTER_SIZE)) {
                Pointer window = link.getPointer(0);
                if (gdk.gdk_window_is_visible(window) == 0) continue;
                Pointer event = gdk.gdk_event_new(31);
                GtkScroll scroll = new GtkScroll(event);
                scroll.window = window;
                scroll.sendEvent = 1;
                scroll.x = scroll.y = scroll.xRoot = scroll.yRoot = 200;
                scroll.direction = 4;
                scroll.dy = delta;
                scroll.write();
                gdk.gdk_event_set_device(event, pointer == null ? device : pointer);
                gdk.gdk_event_set_source_device(event, device);
                gdk.gdk_event_put(event);
                // The queued copy owns references; this original borrows its window.
                scroll.window = null;
                scroll.writeField("window");
                gdk.gdk_event_free(event);
                return;
            }
            fail("No visible GTK test window");
        } finally {
            NativeLibrary.getInstance("glib-2.0").getFunction("g_list_free").invokeVoid(new Object[]{windows});
            objects.getFunction("g_object_unref").invokeVoid(new Object[]{device});
        }
    }

    interface Gdk extends Library {
        NativeLong gdk_wayland_device_get_type();
        Pointer gdk_screen_get_default();
        Pointer gdk_screen_get_toplevel_windows(Pointer screen);
        int gdk_window_is_visible(Pointer window);
        Pointer gdk_event_new(int type);
        void gdk_event_put(Pointer event);
        void gdk_event_free(Pointer event);
        Pointer gdk_display_get_default();
        Pointer gdk_display_get_default_seat(Pointer display);
        Pointer gdk_display_get_device_manager(Pointer display);
        Pointer gdk_seat_get_pointer(Pointer seat);
        void gdk_event_set_device(Pointer event, Pointer device);
        void gdk_event_set_source_device(Pointer event, Pointer device);
    }

    @Structure.FieldOrder({"type", "window", "sendEvent", "time", "x", "y", "state", "direction",
            "device", "xRoot", "yRoot", "dx", "dy", "stop"})
    public static class GtkScroll extends Structure {
        public int type;
        public Pointer window;
        public byte sendEvent;
        public int time;
        public double x, y;
        public int state, direction;
        public Pointer device;
        public double xRoot, yRoot, dx, dy;
        public int stop;
        GtkScroll(Pointer pointer) { super(pointer); read(); }
    }

    private record Fixture(Stage stage, ScrollPane pane) {}

    private static <T> T fx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
