package net.modtale.launcher.hytale;

import java.util.Locale;

public final class HytalePlatform {

    private HytalePlatform() {
    }

    public static String os() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return "windows";
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return "darwin";
        }
        return "linux";
    }

    public static String arch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "arm64";
        }
        return "amd64";
    }

    public static boolean isWindows() {
        return "windows".equals(os());
    }

    public static boolean isMac() {
        return "darwin".equals(os());
    }
}
