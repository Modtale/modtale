package net.modtale.launcher;

import java.awt.AWTError;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.OptionalInt;
import java.util.Properties;

final class LauncherRenderSettings {

    static final String REFRESH_RATE_OVERRIDE_PROPERTY = "modtale.launcher.refreshRate";
    static final String JAVAFX_FRAME_RATE_PROPERTY = "javafx.animation.framerate";
    static final String JAVAFX_PULSE_PROPERTY = "javafx.animation.pulse";
    static final String PRISM_ORDER_PROPERTY = "prism.order";
    static final String PRISM_VSYNC_PROPERTY = "prism.vsync";

    private static final int DEFAULT_REFRESH_RATE = 60;
    private static final int MIN_REFRESH_RATE = 30;
    private static final int MAX_REFRESH_RATE = 1000;
    private static final String WINDOWS_PRISM_ORDER = "d3d,sw";
    private static final String MACOS_PRISM_ORDER = "es2,sw";
    private static final String LINUX_PRISM_ORDER = "es2,sw";

    private LauncherRenderSettings() {
    }

    static void configure() {
        configure(System.getProperties(), System.getProperty("os.name"), detectDisplayRefreshRate());
    }

    static void configure(Properties properties, String osName, OptionalInt detectedRefreshRate) {
        OptionalInt configuredRefreshRate = parseRefreshRate(properties.getProperty(REFRESH_RATE_OVERRIDE_PROPERTY));
        OptionalInt targetRefreshRate = configuredRefreshRate;
        if (configuredRefreshRate.isEmpty()) {
            targetRefreshRate = detectedRefreshRate;
        }
        configureFrameRate(properties, targetRefreshRate, configuredRefreshRate.isPresent());
        setDefault(properties, PRISM_ORDER_PROPERTY, prismOrder(osName));
        setDefault(properties, PRISM_VSYNC_PROPERTY, "true");
    }

    static OptionalInt preferredRefreshRate(int... refreshRates) {
        OptionalInt preferred = OptionalInt.empty();
        for (int refreshRate : refreshRates) {
            OptionalInt normalized = normalizeRefreshRate(refreshRate);
            if (normalized.isPresent() && (preferred.isEmpty() || normalized.getAsInt() > preferred.getAsInt())) {
                preferred = normalized;
            }
        }
        return preferred;
    }

    private static void configureFrameRate(Properties properties, OptionalInt targetRefreshRate, boolean explicitRefreshRate) {
        if (targetRefreshRate.isEmpty()
                || (!explicitRefreshRate && targetRefreshRate.getAsInt() <= DEFAULT_REFRESH_RATE)
                || hasSetting(properties, JAVAFX_FRAME_RATE_PROPERTY)
                || hasSetting(properties, JAVAFX_PULSE_PROPERTY)) {
            return;
        }
        String refreshRate = Integer.toString(targetRefreshRate.getAsInt());
        properties.setProperty(JAVAFX_FRAME_RATE_PROPERTY, refreshRate);
        properties.setProperty(JAVAFX_PULSE_PROPERTY, refreshRate);
    }

    private static OptionalInt detectDisplayRefreshRate() {
        if (GraphicsEnvironment.isHeadless()) {
            return OptionalInt.empty();
        }
        try {
            GraphicsDevice[] devices = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getScreenDevices();
            int[] refreshRates = new int[devices.length];
            for (int i = 0; i < devices.length; i++) {
                refreshRates[i] = devices[i].getDisplayMode().getRefreshRate();
            }
            return preferredRefreshRate(refreshRates);
        } catch (AWTError | RuntimeException ex) {
            return OptionalInt.empty();
        }
    }

    private static OptionalInt parseRefreshRate(String value) {
        if (value == null || value.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            return normalizeRefreshRate(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ex) {
            return OptionalInt.empty();
        }
    }

    private static OptionalInt normalizeRefreshRate(int refreshRate) {
        if (refreshRate == DisplayMode.REFRESH_RATE_UNKNOWN
                || refreshRate < MIN_REFRESH_RATE
                || refreshRate > MAX_REFRESH_RATE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(refreshRate);
    }

    private static String prismOrder(String osName) {
        String normalized = osName == null ? "" : osName.toLowerCase();
        if (normalized.contains("win")) {
            return WINDOWS_PRISM_ORDER;
        }
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return MACOS_PRISM_ORDER;
        }
        return LINUX_PRISM_ORDER;
    }

    private static void setDefault(Properties properties, String key, String value) {
        if (!hasSetting(properties, key)) {
            properties.setProperty(key, value);
        }
    }

    private static boolean hasSetting(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value != null && !value.isBlank();
    }
}
