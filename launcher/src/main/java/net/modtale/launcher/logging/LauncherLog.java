package net.modtale.launcher.logging;

public final class LauncherLog {

    private LauncherLog() {
    }

    public static LauncherLogger getLogger(Class<?> owner) {
        return new LauncherLogger(owner);
    }
}
