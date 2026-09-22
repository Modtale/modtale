package net.modtale.launcher.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HytalePathDetectorTest {

    @Test
    void windowsDefaultsMatchFrontendPostDownloadModal() throws Exception {
        withPlatform("Windows 11", "C:/Users/Alice", () -> {
            Path userData = windowsDataRoot("C:/Users/Alice").resolve("UserData");

            assertEquals(userData.resolve("Mods"), HytalePathDetector.defaultModsDirectory());
            assertEquals(userData.resolve("Saves"), HytalePathDetector.defaultSavesDirectory());
        });
    }

    @Test
    void macDefaultsMatchFrontendPostDownloadModal() throws Exception {
        withPlatform("Mac OS X", "/Users/alice", () -> {
            Path userData = Path.of("/Users/alice", "Library", "Application Support", "Hytale", "UserData");

            assertEquals(userData.resolve("Mods"), HytalePathDetector.defaultModsDirectory());
            assertEquals(userData.resolve("Saves"), HytalePathDetector.defaultSavesDirectory());
        });
    }

    @Test
    void linuxDefaultsMatchFrontendPostDownloadModal() throws Exception {
        withPlatform("Linux", "/home/alice", () -> {
            Path userData = linuxDataRoot("/home/alice").resolve("UserData");

            assertEquals(userData.resolve("Mods"), HytalePathDetector.defaultModsDirectory());
            assertEquals(userData.resolve("Saves"), HytalePathDetector.defaultSavesDirectory());
        });
    }

    @Test
    void detectsOfficialGameAndJavaInstallLayout(@TempDir Path home) throws Exception {
        withPlatform("Linux", home.toString(), () -> {
            Path dataRoot = linuxDataRoot(home.toString());
            Path game = dataRoot.resolve(Path.of("install", "release", "package", "game", "latest"));
            Path client = game.resolve(Path.of("Client", "HytaleClient"));
            Path java = dataRoot.resolve(Path.of("install", "release", "package", "jre", "latest", "bin", "java"));
            Files.createDirectories(client.getParent());
            Files.writeString(client, "");
            Files.createDirectories(java.getParent());
            Files.writeString(java, "");

            assertEquals(game, HytalePathDetector.defaultGameDirectory());
            assertEquals(java, HytalePathDetector.defaultJavaExecutable());
        });
    }

    private static Path windowsDataRoot(String home) {
        String appData = System.getenv("APPDATA");
        return appData == null || appData.isBlank()
                ? Path.of(home, "AppData", "Roaming", "Hytale")
                : Path.of(appData, "Hytale");
    }

    private static Path linuxDataRoot(String home) {
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        return xdgDataHome == null || xdgDataHome.isBlank()
                ? Path.of(home, ".var", "app", "com.hypixel.HytaleLauncher", "data", "Hytale")
                : Path.of(xdgDataHome, "Hytale");
    }

    private static void withPlatform(String osName, String home, CheckedRunnable runnable) throws Exception {
        String previousOs = System.getProperty("os.name");
        String previousHome = System.getProperty("user.home");
        try {
            System.setProperty("os.name", osName);
            System.setProperty("user.home", home);
            runnable.run();
        } finally {
            restoreProperty("os.name", previousOs);
            restoreProperty("user.home", previousHome);
        }
    }

    private static void restoreProperty(String property, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previousValue);
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
