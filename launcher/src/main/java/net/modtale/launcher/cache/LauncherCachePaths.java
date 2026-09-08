package net.modtale.launcher.cache;

import java.nio.file.Path;

public final class LauncherCachePaths {

    private LauncherCachePaths() {
    }

    public static Path rootDirectory() {
        return Path.of(System.getProperty("user.home", "."), ".modtale", "launcher", "cache");
    }

    public static Path cacheDirectory(String name) {
        return rootDirectory().resolve(name);
    }
}
