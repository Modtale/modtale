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

        var update = new UpdateService(api).checkForUpdate(new LauncherSettings(), installed);

        assertEquals("20", update.orElseThrow().newestVersion().id());
        assertEquals("curseforge:42", requested[0]);
    }
}
