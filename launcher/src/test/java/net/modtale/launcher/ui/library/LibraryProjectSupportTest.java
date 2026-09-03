package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.project.ProjectVersion;
import org.junit.jupiter.api.Test;

class LibraryProjectSupportTest {

    @Test
    void versionChoicesKeepReleasesForOtherGameVersions() {
        InstalledProject installed = installedProject("1.0.0", "2026.03.11");
        ProjectVersion installedVersion = version("1.0.0", "2026.03.11", "2026-03-01T00:00:00Z");
        ProjectVersion previousGameVersion = version("0.9.0", "2026.02.14", "2026-02-01T00:00:00Z");

        List<LibraryVersionChoice> choices = LibraryProjectSupport.versionChoices(
                List.of(previousGameVersion, installedVersion),
                installed,
                ""
        );

        assertEquals(List.of("1.0.0", "0.9.0"), choices.stream()
                .map(choice -> choice.version().versionNumber())
                .toList());
        assertTrue(choices.get(1).label().contains("2026.02.14"));
    }

    @Test
    void installGameVersionUsesSelectedReleaseCompatibilityWhenInstalledGameDoesNotMatch() {
        InstalledProject installed = installedProject("1.0.0", "2026.03.11");

        assertEquals("2026.03.11", LibraryProjectSupport.installGameVersion(
                version("1.1.0", "2026.03.11", "2026-04-01T00:00:00Z"),
                installed,
                ""
        ));
        assertEquals("2026.02.14", LibraryProjectSupport.installGameVersion(
                version("0.9.0", "2026.02.14", "2026-02-01T00:00:00Z"),
                installed,
                ""
        ));
    }

    private static InstalledProject installedProject(String version, String gameVersion) {
        return new InstalledProject(
                "project-1",
                "cool-mod",
                "Cool Mod",
                "PLUGIN",
                version,
                version,
                gameVersion,
                Instant.EPOCH,
                Instant.EPOCH,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static ProjectVersion version(String versionNumber, String gameVersion, String releaseDate) {
        return new ProjectVersion(
                versionNumber,
                versionNumber,
                List.of(gameVersion),
                null,
                0,
                releaseDate,
                "",
                List.of(),
                "RELEASE"
        );
    }
}
