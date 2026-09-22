package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleInstalledMod;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.settings.LauncherSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryLocalInstallRecoveryTest {

    @Test
    void recoversUntrackedJarAsLocalInstall(@TempDir Path tempDir) throws Exception {
        Path jar = tempDir.resolve("SimView-0.1.0.jar");
        Files.writeString(jar, "jar");
        LauncherSettings settings = new LauncherSettings();
        settings.setGameVersion("2026.1");
        HytaleInstalledMod mod = new HytaleInstalledMod(
                "net.modtale:SimView",
                "SimView",
                "0.1.0",
                "View distance plugin",
                jar
        );

        LibraryLocalInstallRecovery.RecoveryResult result = LibraryLocalInstallRecovery.recover(
                settings,
                List.of(),
                List.of(mod)
        );

        assertEquals(1, result.recoveredCount());
        InstalledProject recovered = result.projects().getFirst();
        assertEquals(InstalledProject.SOURCE_LOCAL, recovered.source());
        assertEquals("SimView", recovered.title());
        assertEquals("0.1.0", recovered.installedVersion());
        assertEquals(List.of(jar.toString()), recovered.files());
    }

    @Test
    void relinksRecoveredJarWhenManifestPointsToModtaleProject(@TempDir Path tempDir) throws Exception {
        Path jar = tempDir.resolve("SimView-0.1.0.jar");
        writeJarManifest(jar, """
                {
                  "Group": "net.modtale",
                  "Name": "SimView",
                  "Version": "0.1.0",
                  "Website": "https://modtale.net/mod/simview"
                }
                """);
        HytaleInstalledMod mod = new HytaleInstalledMod(
                "net.modtale:SimView",
                "SimView",
                "0.1.0",
                "",
                jar
        );

        LibraryLocalInstallRecovery.RecoveryResult result = LibraryLocalInstallRecovery.recover(
                new LauncherSettings(),
                List.of(),
                List.of(mod)
        );

        InstalledProject recovered = result.projects().getFirst();
        assertEquals(InstalledProject.SOURCE_MODTALE, recovered.source());
        assertEquals("simview", recovered.projectId());
        assertEquals("simview", recovered.slug());
    }

    @Test
    void ignoresJarAlreadyCoveredByInstallRecord(@TempDir Path tempDir) throws Exception {
        Path jar = tempDir.resolve("SimView-0.1.0.jar");
        Files.writeString(jar, "jar");
        InstalledProject recorded = new InstalledProject(
                "project-1",
                "simview",
                "SimView",
                "PLUGIN",
                "0.1.0",
                "version-1",
                "2026.1",
                java.time.Instant.EPOCH,
                java.time.Instant.EPOCH,
                List.of(jar.toString()),
                List.of(),
                List.of()
        );
        HytaleInstalledMod mod = new HytaleInstalledMod(
                "net.modtale:SimView",
                "SimView",
                "0.1.0",
                "",
                jar
        );

        LibraryLocalInstallRecovery.RecoveryResult result = LibraryLocalInstallRecovery.recover(
                new LauncherSettings(),
                List.of(recorded),
                List.of(mod)
        );

        assertEquals(0, result.recoveredCount());
        assertEquals(List.of(recorded), result.projects());
    }

    private static void writeJarManifest(Path jar, String manifest) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new ZipEntry("manifest.json"));
            output.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}
