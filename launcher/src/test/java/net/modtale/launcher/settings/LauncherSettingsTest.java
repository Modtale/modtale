package net.modtale.launcher.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import net.modtale.launcher.hytale.HytaleAuthSession;
import net.modtale.launcher.hytale.HytaleProfile;
import net.modtale.launcher.hytale.HytaleVersion;
import net.modtale.launcher.model.install.InstalledProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LauncherSettingsTest {

    @Test
    void launcherAutoUpdatesDefaultOff() {
        LauncherSettings settings = new LauncherSettings();

        assertFalse(settings.isLauncherAutoUpdates());
    }

    @Test
    void upsertInstalledProjectReplacesExistingProject() {
        LauncherSettings settings = new LauncherSettings();
        InstalledProject first = installed("p1", "1.0.0");
        InstalledProject second = installed("p1", "1.1.0");

        settings.upsertInstalledProject(first);
        settings.upsertInstalledProject(second);

        assertEquals(1, settings.getInstalledProjects().size());
        assertEquals("1.1.0", settings.getInstalledProjects().getFirst().installedVersion());
    }

    @Test
    void tracksAccumulatedHytalePlaytime() {
        LauncherSettings settings = new LauncherSettings();

        settings.setHytalePlaytimeSeconds(-10);
        settings.addHytalePlaytimeSeconds(30);
        settings.addHytalePlaytimeSeconds(45);
        settings.addHytalePlaytimeSeconds(-5);

        assertEquals(75, settings.getHytalePlaytimeSeconds());
    }

    @Test
    void preservesVersionedHytalePatchlineSelection() {
        LauncherSettings settings = new LauncherSettings();

        settings.setHytaleBranch("0.4");

        assertEquals("v0.4", settings.getHytaleBranch());
    }

    @Test
    void settingsStorePersistsHytaleBuildCache(@TempDir Path home) {
        Path settingsPath = home.resolve("settings.json");
        LauncherSettings settings = new LauncherSettings();
        settings.cacheHytalePatchlines("owner", "linux/amd64", List.of("release", "v0.4"));
        settings.cachePendingHytalePatchlines("owner", "linux/amd64", List.of("pre-release", "v0.4"));
        settings.cacheHytaleVersions("owner", "linux/amd64", "v0.4", List.of(
                new HytaleVersion("v0.4", 7, 0, true, "0.4.9", "pwr", "head", "sig")
        ));

        SettingsStore store = new SettingsStore(settingsPath);
        store.save(settings);

        LauncherSettings loaded = store.load();

        assertEquals(List.of("release", "v0.4"), loaded.cachedHytalePatchlines("owner", "linux/amd64"));
        assertEquals(List.of("pre-release", "v0.4"), loaded.pendingHytalePatchlines("owner", "linux/amd64"));
        List<HytaleVersion> versions = loaded.cachedHytaleVersions("owner", "linux/amd64", "0.4");
        assertEquals(1, versions.size());
        assertEquals("v0.4", versions.getFirst().branch());
        assertEquals(7, versions.getFirst().build());
        assertEquals("0.4.9", versions.getFirst().gameVersion());
    }

    @Test
    void settingsStoreRestoresInstalledProjectsFromLocalRegistry(@TempDir Path home) throws Exception {
        Path settingsPath = home.resolve("settings.json");
        Path installedFile = home.resolve(Path.of("mods", "mod.jar"));
        Files.createDirectories(installedFile.getParent());
        Files.writeString(installedFile, "installed");

        SettingsStore store = new SettingsStore(settingsPath);
        LauncherSettings settings = new LauncherSettings();
        settings.setInstalledProjects(List.of(installedWithFile("p1", "1.0.0", installedFile)));
        store.save(settings);

        Files.writeString(settingsPath, """
                {
                  "installedProjects": []
                }
                """);

        LauncherSettings loaded = store.load();

        assertEquals(1, loaded.getInstalledProjects().size());
        assertEquals("p1", loaded.getInstalledProjects().getFirst().projectId());
    }

    @Test
    void settingsStoreRemoveInstalledProjectClearsLocalRegistry(@TempDir Path home) throws Exception {
        Path settingsPath = home.resolve("settings.json");
        Path installedFile = home.resolve(Path.of("mods", "mod.jar"));
        Files.createDirectories(installedFile.getParent());
        Files.writeString(installedFile, "installed");

        SettingsStore store = new SettingsStore(settingsPath);
        LauncherSettings settings = new LauncherSettings();
        settings.setInstalledProjects(List.of(installedWithFile("p1", "1.0.0", installedFile)));
        store.save(settings);

        store.removeInstalledProject("p1");
        LauncherSettings empty = new LauncherSettings();
        empty.setInstalledProjects(List.of());
        store.save(empty);

        assertTrue(store.load().getInstalledProjects().isEmpty());
    }

    @Test
    void tracksMultipleHytaleAccountsAndActiveProfile() {
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession first = hytaleSession("owner-a", "Alpha", "alpha-profile");
        HytaleAuthSession second = hytaleSession("owner-b", "Beta", "beta-profile");

        settings.upsertHytaleAuthSession(first);
        settings.upsertHytaleAuthSession(second);
        settings.selectHytaleAccount("owner-a");

        assertEquals(2, settings.getHytaleAuthSessions().size());
        assertEquals("owner-a", settings.getActiveHytaleAccountId());
        assertEquals("Alpha", settings.getHytaleAuthSession().getUsername());

        settings.removeActiveHytaleAuthSession();

        assertEquals(1, settings.getHytaleAuthSessions().size());
        assertEquals("owner-b", settings.getActiveHytaleAccountId());
        assertEquals("Beta", settings.getHytaleAuthSession().getUsername());
    }

    @Test
    void removesSpecificHytaleAccountWithoutChangingCurrentSelection() {
        LauncherSettings settings = new LauncherSettings();
        HytaleAuthSession first = hytaleSession("owner-a", "Alpha", "alpha-profile");
        HytaleAuthSession second = hytaleSession("owner-b", "Beta", "beta-profile");
        HytaleAuthSession third = hytaleSession("owner-c", "Gamma", "gamma-profile");

        settings.upsertHytaleAuthSession(first);
        settings.upsertHytaleAuthSession(second);
        settings.upsertHytaleAuthSession(third);
        settings.removeHytaleAuthSession("owner-a");

        assertEquals(2, settings.getHytaleAuthSessions().size());
        assertEquals("owner-c", settings.getActiveHytaleAccountId());
        assertEquals("Gamma", settings.getHytaleAuthSession().getUsername());
    }

    @Test
    void settingsStoreUpgradesStaleHytaleLaunchDefaults(@TempDir Path home) throws Exception {
        withPlatform("Mac OS X", home.toString(), () -> {
            Path dataRoot = home.resolve(Path.of("Library", "Application Support", "Hytale"));
            Path game = dataRoot.resolve(Path.of("install", "release", "package", "game", "latest"));
            Path client = game.resolve(Path.of("Client", "Hytale.app", "Contents", "MacOS", "HytaleClient"));
            Path java = dataRoot.resolve(Path.of("install", "release", "package", "jre", "latest", "bin", "java"));
            Files.createDirectories(client.getParent());
            Files.writeString(client, "");
            Files.createDirectories(java.getParent());
            Files.writeString(java, "");

            Path settingsPath = home.resolve("settings.json");
            Files.writeString(settingsPath, """
                    {
                      "hytaleModsPath": "%s",
                      "hytaleGamePath": "%s",
                      "hytaleUserDataPath": "%s",
                      "hytaleJavaPath": "%s",
                      "hytaleBranch": "release"
                    }
                    """.formatted(
                    home.resolve("old-mods").toString().replace("\\", "\\\\"),
                    home.resolve("old-game").toString().replace("\\", "\\\\"),
                    dataRoot.resolve("UserData").toString().replace("\\", "\\\\"),
                    currentJava().toString().replace("\\", "\\\\")
            ));

            LauncherSettings settings = new SettingsStore(settingsPath).load();

            assertEquals(game, settings.hytaleGameDirectory());
            assertEquals(java, settings.hytaleJavaExecutable());
        });
    }

    @Test
    void settingsStoreRecreatesBlankSettingsFile(@TempDir Path home) throws Exception {
        Path settingsPath = home.resolve("settings.json");
        Files.writeString(settingsPath, " \n\t ");

        LauncherSettings settings = new SettingsStore(settingsPath).load();

        assertEquals(HytalePathDetector.defaultModsDirectory().toString(), settings.getHytaleModsPath());
        assertTrue(Files.size(settingsPath) > 0);
    }

    private static InstalledProject installed(String projectId, String version) {
        return new InstalledProject(projectId, "slug", "Title", "PLUGIN", version, version, "1.0",
                Instant.EPOCH, Instant.EPOCH, List.of(), List.of(), List.of());
    }

    private static InstalledProject installedWithFile(String projectId, String version, Path file) {
        return new InstalledProject(projectId, "slug", "Title", "PLUGIN", version, version, "1.0",
                Instant.EPOCH, Instant.EPOCH, List.of(file.toString()), List.of(), List.of());
    }

    private static HytaleAuthSession hytaleSession(String owner, String username, String uuid) {
        HytaleAuthSession session = new HytaleAuthSession();
        session.setAccountOwnerId(owner);
        session.setUsername(username);
        session.setUuid(uuid);
        session.setProfiles(List.of(new HytaleProfile(username, uuid, owner)));
        return session;
    }

    private static Path currentJava() {
        return Path.of(System.getProperty("java.home", "."), "bin", "java");
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
