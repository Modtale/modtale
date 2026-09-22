package net.modtale.launcher.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import java.util.List;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.settings.LauncherSettings;
import org.junit.jupiter.api.Test;

class UpdateServiceTest {
    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path tempDir;

    @Test
    void usesActualServerVersionInsteadOfRecordedCatalogChoice() throws Exception {
        java.nio.file.Path game = java.nio.file.Files.createDirectories(tempDir.resolve("Server"));
        var manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Implementation-Version", "0.5.6");
        try (var jar = new java.util.jar.JarOutputStream(
                java.nio.file.Files.newOutputStream(game.resolve("HytaleServer.jar")), manifest)) { }
        ProjectVersion current = new ProjectVersion("8230539", "BetterMap-1.3.7.jar", List.of("0.5"), "", 0,
                "2026-06-11T08:20:18Z", "", List.of(), "RELEASE");
        ProjectVersion next = new ProjectVersion("8747205", "BetterMap-1.3.8.jar", List.of("0.6"), "", 0,
                "2026-08-27T14:53:23Z", "", List.of(), "RELEASE");
        ProjectDetail detail = new ProjectDetail("curseforge:1430352", "bettermap", "BetterMap", "", "", "PLUGIN",
                "", "", "", List.of(), List.of(next, current));
        ModtaleApiClient api = new ModtaleApiClient("https://api.example.test/api/v1") {
            @Override public ProjectDetail getProject(String route) { return detail; }
        };
        InstalledProject installed = new InstalledProject(detail.id(), "bettermap", "BetterMap", "PLUGIN", "1.3.6",
                "8149119", "0.6", Instant.EPOCH, Instant.EPOCH, List.of("mod.jar"), List.of(), List.of(),
                InstalledProject.SOURCE_CURSEFORGE, InstalledProject.INSTALL_DIRECT, false, List.of());
        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleGamePath(tempDir.toString());
        settings.setGameVersion("0.6");
        assertEquals(current, new UpdateService(api).checkForUpdate(settings, installed).orElseThrow().newestVersion());
    }

    @Test
    void checksManagedCurseForgeInstallsByProviderProjectId() {
        ProjectVersion latest = new ProjectVersion("20", "2.0", List.of("2026.1"), "", 0, "", "",
                List.of(), "RELEASE");
        ProjectDetail detail = new ProjectDetail("curseforge:42", "my-mod", "My Mod", "", "", "PLUGIN", "", "",
                "", List.of(), List.of(latest));
        String[] requested = {""};
        ModtaleApiClient api = new ModtaleApiClient("https://api.example.test/api/v1") {
            @Override public ProjectDetail getProject(String route) { requested[0] = route; return detail; }
        };
        InstalledProject installed = new InstalledProject("curseforge:42", "my-mod", "My Mod", "PLUGIN", "1.0",
                "10", "2026.1", Instant.EPOCH, Instant.EPOCH, List.of("mod.jar"), List.of(), List.of(),
                InstalledProject.SOURCE_CURSEFORGE, InstalledProject.INSTALL_DIRECT, false, List.of());

        LauncherSettings settings = new LauncherSettings();
        settings.setHytaleGamePath("");
        var update = new UpdateService(api).checkForUpdate(settings, installed);

        assertEquals("20", update.orElseThrow().newestVersion().id());
        assertEquals("curseforge:42", requested[0]);
    }
}
