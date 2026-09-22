package net.modtale.launcher.model.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;
import net.modtale.launcher.hytale.HytaleAuthSession;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.settings.LauncherSettings;
import org.junit.jupiter.api.Test;

class LauncherSettingsSnapshotTest {

    @Test
    void hashIgnoresSecretsPlaytimeAndInstalledFilePaths() {
        LauncherSettings first = settings("/mods/a.jar");
        LauncherSettings second = settings("/mods/b.jar");
        second.setLastUsername("different");
        second.setHytalePlaytimeSeconds(999);
        HytaleAuthSession session = new HytaleAuthSession();
        session.setAccessToken("secret");
        second.setHytaleAuthSession(session);

        LauncherSettingsSnapshot firstSnapshot = LauncherSettingsSnapshot.fromSettings(first);
        LauncherSettingsSnapshot secondSnapshot = LauncherSettingsSnapshot.fromSettings(second);

        assertEquals(firstSnapshot.effectiveHash(), secondSnapshot.effectiveHash());
        assertFalse(firstSnapshot.installedProjects().getFirst().getDependencyProjectIds().isEmpty());
    }

    @Test
    void hashChangesForPreferenceAndInstalledVersionChanges() {
        LauncherSettings first = settings("/mods/a.jar");
        LauncherSettings second = settings("/mods/a.jar");
        second.setGameVersion("2.0");

        LauncherSettingsSnapshot firstSnapshot = LauncherSettingsSnapshot.fromSettings(first);
        LauncherSettingsSnapshot secondSnapshot = LauncherSettingsSnapshot.fromSettings(second);

        assertFalse(firstSnapshot.effectiveHash().equals(secondSnapshot.effectiveHash()));
    }

    @Test
    void computedHashIgnoresStoredHashAndTimestampMetadata() {
        LauncherSettingsSnapshot local = LauncherSettingsSnapshot.fromSettings(settings("/mods/a.jar"));
        LauncherSettingsSnapshot remote = LauncherSettingsSnapshot.fromSettings(settings("/mods/a.jar"));
        remote.setSettingsHash("legacy-server-hash");
        remote.setUpdatedAt("2026-06-19T22:17:00Z");

        assertEquals(local.computeHash(), remote.computeHash());
        assertFalse(local.effectiveHash().equals(remote.effectiveHash()));
    }

    @Test
    void effectiveHashKeepsUploadedFingerprintWhenServerReturnsTrimmedSnapshot() {
        LauncherSettingsSnapshot local = LauncherSettingsSnapshot.fromSettings(settings("/mods/a.jar"));
        LauncherSettingsSnapshot remote = new LauncherSettingsSnapshot();
        remote.setSettingsHash(local.computeHash());
        remote.setPreferences(local.getPreferences());

        LauncherSettingsSnapshot.InstalledProjectSnapshot installed =
                new LauncherSettingsSnapshot.InstalledProjectSnapshot();
        installed.setProjectId("project-1");
        installed.setInstalledVersion("1.2.3");
        installed.setInstalledVersionId("version-1");
        installed.setGameVersion("1.0");
        installed.setDependencyProjectIds(List.of("dep-1"));
        installed.setExternalDependencies(List.of("external-one"));
        remote.setInstalledProjects(List.of(installed));

        assertFalse(local.computeHash().equals(remote.computeHash()));
        assertEquals(local.computeHash(), remote.effectiveHash());
    }

    @Test
    void preferencesOnlyPayloadKeepsFullFingerprintWithoutInstalledProjects() {
        LauncherSettingsSnapshot local = LauncherSettingsSnapshot.fromSettings(settings("/mods/a.jar"));
        LauncherSettingsSnapshot preferencesOnly = local.preferencesOnly();

        assertEquals(local.computeHash(), preferencesOnly.getSettingsHash());
        assertEquals(local.getPreferences().getGameVersion(), preferencesOnly.getPreferences().getGameVersion());
        assertEquals(0, preferencesOnly.installedProjects().size());
        assertFalse(local.installedProjectsHash().equals(preferencesOnly.installedProjectsHash()));
    }

    @Test
    void installedProjectsHashIgnoresPreferenceChanges() {
        LauncherSettings first = settings("/mods/a.jar");
        LauncherSettings second = settings("/mods/a.jar");
        second.setGameVersion("2.0");

        assertEquals(
                LauncherSettingsSnapshot.fromSettings(first).installedProjectsHash(),
                LauncherSettingsSnapshot.fromSettings(second).installedProjectsHash()
        );
    }

    @Test
    void configContentsAndPathsAffectHashAndTravelInPreferenceOnlyUpdates() {
        LauncherSettings settings = settings("/mods/a.jar");
        var before = LauncherSettingsSnapshot.fromSettings(settings);
        var configs = List.of(new LauncherConfigSnapshot("Mods/Example/config.json", "{\"value\":2}"));
        settings.setConfigs(configs);
        var after = LauncherSettingsSnapshot.fromSettings(settings);
        assertFalse(before.computeHash().equals(after.computeHash()));
        assertEquals(before.installedProjectsHash(), after.installedProjectsHash());
        assertEquals(2, after.getSchemaVersion());
        assertEquals(configs, after.preferencesOnly().getConfigs());
        settings.setConfigs(List.of(new LauncherConfigSnapshot("Mods/Example/config.json", "{\"value\":3}")));
        assertFalse(after.computeHash().equals(LauncherSettingsSnapshot.fromSettings(settings).computeHash()));
    }

    @Test
    void configHashIsStableAcrossOrderingAndRoundTrips() throws Exception {
        var configs = List.of(new LauncherConfigSnapshot("Mods/Example/a.json", "{}"),
                new LauncherConfigSnapshot("Saves/My World/mods/Example/b.ini", "value=2"));
        LauncherSettings settings = settings("/mods/a.jar");
        settings.setConfigs(configs);
        var snapshot = LauncherSettingsSnapshot.fromSettings(settings);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var restored = mapper.readValue(mapper.writeValueAsString(snapshot), LauncherSettingsSnapshot.class);
        restored.setConfigs(configs.reversed());
        assertEquals(snapshot.computeHash(), restored.computeHash());
    }

    private static LauncherSettings settings(String filePath) {
        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleModsPath("/mods");
        settings.setHytaleGamePath("/game");
        settings.setHytaleUserDataPath("/user-data");
        settings.setHytaleJavaPath("/java");
        settings.setGameVersion("1.0");
        settings.setIncludeDependencies(true);
        settings.setInstalledProjects(List.of(new InstalledProject(
                "project-1",
                "slug",
                "Title",
                "PLUGIN",
                "1.2.3",
                "version-1",
                "1.0",
                Instant.EPOCH,
                Instant.EPOCH,
                List.of(filePath),
                List.of("dep-1"),
                List.of("external-one")
        )));
        return settings;
    }
}
