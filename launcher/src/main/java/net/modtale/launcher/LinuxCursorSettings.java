package net.modtale.launcher;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class LinuxCursorSettings {

    private static final String CURSOR_SIZE = "XCURSOR_SIZE";
    private static final String CURSOR_THEME = "XCURSOR_THEME";
    private static final String RESOURCE_SIZE = "size";
    private static final String RESOURCE_THEME = "theme";

    private LinuxCursorSettings() {
    }

    static Map<String, String> configure() {
        String osName = System.getProperty("os.name", "");
        String displayName = System.getenv("DISPLAY");
        if (!isLinuxDisplay(osName, displayName)) {
            return Map.of();
        }

        Pointer display = null;
        try {
            X11 x11 = X11.INSTANCE;
            display = x11.XOpenDisplay(displayName);
            if (display == null) {
                return Map.of();
            }
            Map<String, String> resources = new LinkedHashMap<>();
            resources.put(RESOURCE_SIZE, resourceValue(x11.XGetDefault(display, "Xcursor", RESOURCE_SIZE)));
            resources.put(RESOURCE_THEME, resourceValue(x11.XGetDefault(display, "Xcursor", RESOURCE_THEME)));

            Map<String, String> settings = missingCursorEnvironment(osName, displayName, System.getenv(), resources);
            settings.forEach((name, value) -> LibC.INSTANCE.setenv(name, value, 0));
            return settings;
        } catch (LinkageError | RuntimeException ignored) {
            return Map.of();
        } finally {
            if (display != null) {
                X11.INSTANCE.XCloseDisplay(display);
            }
        }
    }

    static Map<String, String> missingCursorEnvironment(
            String osName,
            String displayName,
            Map<String, String> environment,
            Map<String, String> resources
    ) {
        if (!isLinuxDisplay(osName, displayName)) {
            return Map.of();
        }
        Map<String, String> settings = new LinkedHashMap<>();
        String size = value(resources, RESOURCE_SIZE);
        if (isBlank(environment.get(CURSOR_SIZE)) && validSize(size)) {
            settings.put(CURSOR_SIZE, size.trim());
        }
        String theme = value(resources, RESOURCE_THEME);
        if (isBlank(environment.get(CURSOR_THEME)) && !isBlank(theme)) {
            settings.put(CURSOR_THEME, theme.trim());
        }
        return Map.copyOf(settings);
    }

    private static boolean isLinuxDisplay(String osName, String displayName) {
        return osName != null
                && osName.toLowerCase(Locale.ROOT).contains("linux")
                && !isBlank(displayName);
    }

    private static boolean validSize(String value) {
        try {
            int size = Integer.parseInt(value == null ? "" : value.trim());
            return size >= 1 && size <= 256;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String value(Map<String, String> values, String key) {
        return values == null ? "" : values.getOrDefault(key, "");
    }

    private static String resourceValue(Pointer value) {
        return value == null ? "" : value.getString(0);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private interface LibC extends Library {
        LibC INSTANCE = Native.load(Platform.C_LIBRARY_NAME, LibC.class);

        int setenv(String name, String value, int overwrite);
    }

    private interface X11 extends Library {
        X11 INSTANCE = Native.load("X11", X11.class);

        Pointer XOpenDisplay(String displayName);

        Pointer XGetDefault(Pointer display, String program, String option);

        int XCloseDisplay(Pointer display);
    }
}
