package net.modtale.launcher.ui.common;

import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.IntByReference;
import net.modtale.launcher.ui.common.NativeScrollInput.Sample;
import java.util.Locale;
import net.modtale.launcher.logging.LauncherLog;

/** Reads scroll metadata before Glass discards GTK's smooth deltas and device type. */
final class LinuxScrollInput {
    private static boolean attempted;
    private static Sample current;
    // Native registrations retain a function pointer, not the Java callback object.
    private static Hook hook;
    private static NativeLibrary glass;
    private static Pointer registration;

    private LinuxScrollInput() {}

    static void install() {
        if (attempted) return;
        attempted = true;
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) return;
        try {
            glass = net.modtale.launcher.platform.LinuxDesktopBackend.glassLibrary();
            if (glass == null) return;
            Gdk gdk = Native.load("gdk-3", Gdk.class);
            Glib glib = Native.load("glib-2.0", Glib.class);
            DoubleByReference dx = new DoubleByReference();
            DoubleByReference dy = new DoubleByReference();
            IntByReference direction = new IntByReference();
            hook = (event, ignored) -> {
                current = null;
                if (event.getInt(0) != 31) return; // GDK_SCROLL
                boolean smooth = gdk.gdk_event_get_scroll_deltas(event, dx, dy) != 0;
                if (!smooth) {
                    if (gdk.gdk_event_get_scroll_direction(event, direction) == 0) return;
                    dx.setValue(direction.getValue() == 2 ? -1 : direction.getValue() == 3 ? 1 : 0);
                    dy.setValue(direction.getValue() == 0 ? -1 : direction.getValue() == 1 ? 1 : 0);
                }
                Pointer device = gdk.gdk_event_get_source_device(event);
                int source = device == null ? -1 : gdk.gdk_device_get_source(device);
                long eventMillis = Integer.toUnsignedLong(gdk.gdk_event_get_time(event));
                long elapsed = ((glib.g_get_monotonic_time() / 1000) - eventMillis) & 0xffff_ffffL;
                long delay = eventMillis == 0 || elapsed > 250 ? 0 : elapsed * 1_000_000;
                current = new Sample(dx.getValue() * 120, dy.getValue() * 120,
                        smooth && (source == 6 || source == 5 || source == -1), delay);
            };
            // This event-loop hook is provided by our pinned JavaFX GTK backend.
            // It observes events without replacing GTK's handler or changing native memory.
            registration = glass.getFunction("_Z21glass_evloop_hook_addPFvP9_GdkEventPvES1_")
                    .invokePointer(new Object[]{hook, null});
        } catch (RuntimeException | LinkageError failure) {
            LauncherLog.getLogger(LinuxScrollInput.class).warn("Native smooth-scroll metadata unavailable: " + failure);
        }
    }

    static Sample take() {
        Sample sample = current;
        current = null;
        return sample;
    }

    interface Hook extends Callback {
        void invoke(Pointer event, Pointer data);
    }

    interface Gdk extends com.sun.jna.Library {
        int gdk_event_get_scroll_direction(Pointer event, IntByReference direction);
        int gdk_event_get_scroll_deltas(Pointer event, DoubleByReference x, DoubleByReference y);
        Pointer gdk_event_get_source_device(Pointer event);
        int gdk_device_get_source(Pointer device);
        int gdk_event_get_time(Pointer event);
    }

    interface Glib extends com.sun.jna.Library {
        long g_get_monotonic_time();
    }

}
