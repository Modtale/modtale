package net.modtale.launcher.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectVersion;
import org.junit.jupiter.api.Test;

class VersionSelectorTest {

    @Test
    void selectsNewestCompatibleRelease() {
        ProjectVersion alpha = version("1.2.0-alpha", "2026-02-01T00:00:00Z", "ALPHA", "1.0");
        ProjectVersion oldRelease = version("1.1.0", "2026-01-01T00:00:00Z", "RELEASE", "1.0");
        ProjectVersion newRelease = version("1.2.0", "2026-03-01T00:00:00Z", "RELEASE", "1.0");
        ProjectDetail project = new ProjectDetail("p1", "project", "Project", "", "dev", "PLUGIN",
                "", "MIT", "", List.of(), List.of(alpha, oldRelease, newRelease));

        ProjectVersion selected = VersionSelector.latestCompatible(project, "1.0").orElseThrow();

        assertEquals("1.2.0", selected.versionNumber());
    }

    @Test
    void filtersByGameVersion() {
        ProjectVersion wrongGame = version("2.0.0", "2026-04-01T00:00:00Z", "RELEASE", "2.0");
        ProjectVersion rightGame = version("1.0.1", "2026-01-01T00:00:00Z", "RELEASE", "1.0");

        ProjectVersion selected = VersionSelector.latestCompatible(List.of(wrongGame, rightGame), "1.0").orElseThrow();

        assertEquals("1.0.1", selected.versionNumber());
    }

    @Test
    void returnsEmptyWhenNoVersionMatches() {
        ProjectVersion wrongGame = version("2.0.0", "2026-04-01T00:00:00Z", "RELEASE", "2.0");

        assertTrue(VersionSelector.latestCompatible(List.of(wrongGame), "1.0").isEmpty());
    }

    private static ProjectVersion version(String number, String releaseDate, String channel, String gameVersion) {
        return new ProjectVersion(number, number, List.of(gameVersion), null, 0, releaseDate, "", List.of(), channel);
    }
}
