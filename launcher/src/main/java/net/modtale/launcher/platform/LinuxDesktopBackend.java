package net.modtale.launcher.platform;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/** Native GTK integration shared by the renderer, input adapters, and window chrome. */
public final class LinuxDesktopBackend {
    private static final Map<String, ?> LOCAL_SYMBOLS = Map.of(Library.OPTION_OPEN_FLAGS, 1);
    private static NativeLibrary glass;
    private static volatile Boolean nativeWayland;
    private static Function moveResize;

    private LinuxDesktopBackend() {}

    public static OptionalInt prepareDisplay() {
        if (!isLinux() || System.getenv("WAYLAND_DISPLAY") == null
                || LinuxDesktopBackend.class.getResource("/META-INF/modtale-native-wayland") == null) {
            return OptionalInt.empty();
        }
        try {
            Gdk gdk = Native.load("gdk-3", Gdk.class, LOCAL_SYMBOLS);
            gdk.gdk_set_allowed_backends("wayland");
            gdk.gdk_threads_init();
            if (gdk.gdk_init_check(null, null) == 0) return OptionalInt.empty();
            Pointer display = gdk.gdk_display_get_default();
            int rate = 0;
            for (int i = 0; i < gdk.gdk_display_get_n_monitors(display); i++) {
                rate = Math.max(rate, gdk.gdk_monitor_get_refresh_rate(gdk.gdk_display_get_monitor(display, i)));
            }
            return rate > 0 ? OptionalInt.of(Math.round(rate / 1000f)) : OptionalInt.empty();
        } catch (RuntimeException | LinkageError unavailable) {
            return OptionalInt.empty();
        }
    }

    public static synchronized NativeLibrary glassLibrary() {
        if (glass != null || !isLinux()) return glass;
        try (var mappings = Files.lines(Path.of("/proc/self/maps"))) {
            String path = mappings.map(line -> line.split("\\s+", 6))
                    .filter(fields -> fields.length == 6 && fields[5].endsWith("/libglassgtk3.so"))
                    .map(fields -> fields[5].replace("\\040", " "))
                    .findFirst().orElse(null);
            // Keep Glass's statically linked C++ runtime out of the global symbol namespace.
            if (path != null) glass = NativeLibrary.getInstance(path, LOCAL_SYMBOLS);
        } catch (IOException | RuntimeException | LinkageError unavailable) {
            return null;
        }
        return glass;
    }

    public static boolean isWayland() {
        Boolean active = nativeWayland;
        if (active != null) return active;
        if (glassLibrary() == null) return false;
        Function query = function("modtale_glass_is_wayland");
        active = query != null && query.invokeInt(new Object[0]) != 0;
        nativeWayland = active;
        return active;
    }

    public static boolean beginMoveResize(int direction) {
        if (!isWayland()) return false;
        if (moveResize == null) moveResize = function("modtale_glass_begin_move_resize");
        return moveResize != null && moveResize.invokeInt(new Object[]{direction}) != 0;
    }

    private static Function function(String name) {
        NativeLibrary library = glassLibrary();
        if (library == null) return null;
        try { return library.getFunction(name); }
        catch (UnsatisfiedLinkError unavailable) { return null; }
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    private interface Gdk extends Library {
        void gdk_set_allowed_backends(String backends);
        void gdk_threads_init();
        int gdk_init_check(Pointer argc, Pointer argv);
        Pointer gdk_display_get_default();
        int gdk_display_get_n_monitors(Pointer display);
        Pointer gdk_display_get_monitor(Pointer display, int index);
        int gdk_monitor_get_refresh_rate(Pointer monitor);
    }
}
