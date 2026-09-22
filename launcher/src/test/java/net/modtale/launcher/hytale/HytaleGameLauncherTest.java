package net.modtale.launcher.hytale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.modtale.launcher.settings.LauncherSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HytaleGameLauncherTest {

    @Test
    void authenticatedArgumentsNeverUseOfflineMode() {
        HytaleAuthSession session = authenticatedSession();

        List<String> args = HytaleGameLauncher.authenticatedArguments(
                Path.of("/games/Hytale/build-42"),
                Path.of("/users/me/Hytale"),
                Path.of("/java/bin/java"),
                session
        );

        assertTrue(args.contains("--auth-mode"));
        assertEquals("authenticated", args.get(args.indexOf("--auth-mode") + 1));
        assertFalse(args.contains("offline"));
        assertFalse(args.contains("HYTALE_OFFLINE_TOKEN"));
        assertTrue(args.contains("--identity-token"));
        assertTrue(args.contains("--session-token"));
    }

    @Test
    void authenticatedArgumentsRequireLaunchTokens() {
        assertThrows(HytaleApiException.class, () -> HytaleGameLauncher.authenticatedArguments(
                Path.of("/games/Hytale/build-42"),
                Path.of("/users/me/Hytale"),
                Path.of("/java/bin/java"),
                new HytaleAuthSession()
        ));
    }

    @Test
    void resolvePathsAcceptsOfficialDataRootAndUsesBundledJava(@TempDir Path tempDir) throws Exception {
        withPlatform("Linux", () -> {
            Path dataRoot = tempDir.resolve("Hytale");
            Path game = dataRoot.resolve(Path.of("install", "release", "package", "game", "latest"));
            Path client = game.resolve(Path.of("Client", "HytaleClient"));
            Path bundledJava = dataRoot.resolve(Path.of("install", "release", "package", "jre", "latest", "bin", "java"));
            Files.createDirectories(client.getParent());
            Files.writeString(client, "");
            Files.createDirectories(bundledJava.getParent());
            Files.writeString(bundledJava, "");

            LauncherSettings settings = new LauncherSettings();
            settings.setHytaleGamePath(dataRoot.toString());
            settings.setHytaleUserDataPath(dataRoot.resolve("UserData").toString());
            settings.setHytaleJavaPath(currentJava().toString());

            HytaleGameLauncher.LaunchPaths paths = HytaleGameLauncher.resolvePaths(settings);

            assertEquals(game, paths.gameDirectory());
            assertEquals(client, paths.executable());
            assertEquals(bundledJava, paths.javaExecutable());
            assertEquals(game.resolve("Client"), paths.workingDirectory());
        });
    }

    @Test
    void resolvePathsUsesSelectedVersionedPatchlineFolder(@TempDir Path tempDir) throws Exception {
        withPlatform("Linux", () -> {
            Path dataRoot = tempDir.resolve("Hytale");
            Path game = dataRoot.resolve(Path.of("install", "v0.4", "package", "game", "latest"));
            Path client = game.resolve(Path.of("Client", "HytaleClient"));
            Path bundledJava = dataRoot.resolve(Path.of("install", "v0.4", "package", "jre", "latest", "bin", "java"));
            Files.createDirectories(client.getParent());
            Files.writeString(client, "");
            Files.createDirectories(bundledJava.getParent());
            Files.writeString(bundledJava, "");

            LauncherSettings settings = new LauncherSettings();
            settings.setHytaleBranch("v0.4");
            settings.setHytaleGamePath(dataRoot.toString());
            settings.setHytaleUserDataPath(dataRoot.resolve("UserData").toString());
            settings.setHytaleJavaPath(currentJava().toString());

            HytaleGameLauncher.LaunchPaths paths = HytaleGameLauncher.resolvePaths(settings);

            assertEquals(game, paths.gameDirectory());
            assertEquals(client, paths.executable());
            assertEquals(bundledJava, paths.javaExecutable());
        });
    }

    @Test
    void customJavaExecutableWinsOverBundledJava(@TempDir Path tempDir) throws Exception {
        withPlatform("Linux", () -> {
            Path dataRoot = tempDir.resolve("Hytale");
            Path game = dataRoot.resolve(Path.of("install", "release", "package", "game", "latest"));
            Path bundledJava = dataRoot.resolve(Path.of("install", "release", "package", "jre", "latest", "bin", "java"));
            Path customJava = tempDir.resolve("custom-java");
            Files.createDirectories(bundledJava.getParent());
            Files.writeString(bundledJava, "");
            Files.writeString(customJava, "");

            assertEquals(customJava, HytaleGameLauncher.resolveJavaExecutable(
                    customJava,
                    game,
                    dataRoot.resolve("UserData"),
                    "release"
            ));
        });
    }

    private static HytaleAuthSession authenticatedSession() {
        HytaleAuthSession session = new HytaleAuthSession();
        session.setUsername("Player");
        session.setUuid("00000000-0000-0000-0000-000000000000");
        session.setIdentityToken("identity-token");
        session.setSessionToken("session-token");
        return session;
    }

    private static Path currentJava() {
        return Path.of(System.getProperty("java.home", "."), "bin", "java");
    }

    private static void withPlatform(String osName, CheckedRunnable runnable) throws Exception {
        String previousOs = System.getProperty("os.name");
        try {
            System.setProperty("os.name", osName);
            runnable.run();
        } finally {
            if (previousOs == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", previousOs);
            }
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
