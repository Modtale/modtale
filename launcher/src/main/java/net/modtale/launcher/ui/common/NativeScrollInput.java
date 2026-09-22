package net.modtale.launcher.ui.common;

import java.util.Locale;

/** Native metadata adapters feed logical pixels into the shared scroll controller. */
final class NativeScrollInput {
    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean MAC = OS.contains("mac") || OS.contains("darwin");

    private NativeScrollInput() {}

    static void install() {
        if (MAC) MacScrollInput.install();
        else LinuxScrollInput.install();
    }

    static boolean animationsEnabled() {
        if (MAC) return MacScrollInput.animationsEnabled();
        if (OS.contains("win")) return WindowsScrollInput.animationsEnabled();
        return true;
    }

    static Sample take(double outputScale) {
        if (MAC) return MacScrollInput.current();
        Sample sample = LinuxScrollInput.take();
        return sample == null ? null : new Sample(sample.x / outputScale, sample.y / outputScale,
                sample.precise, sample.delayNanos);
    }

    record Sample(double x, double y, boolean precise, long delayNanos) {}
}
