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

    @Test
    void comparesVersionNumbersSemanticallyWhenReleaseDatesMatch() {
        ProjectVersion older = version("1.9.0", "2026-04-01T00:00:00Z", "RELEASE", "1.0");
        ProjectVersion newer = version("1.10.0", "2026-04-01T00:00:00Z", "RELEASE", "1.0");

        ProjectVersion selected = VersionSelector.latestCompatible(List.of(older, newer), "1.0").orElseThrow();

        assertEquals("1.10.0", selected.versionNumber());
    }

    private static ProjectVersion version(String number, String releaseDate, String channel, String gameVersion) {
        return new ProjectVersion(number, number, List.of(gameVersion), null, 0, releaseDate, "", List.of(), channel);
    }

    @Test
    void selectsLaterPatchWithModtaleLocalReleaseDates() {
        ProjectVersion older = version("0.9.0", "2026-07-01T12:00:00.123456789", "BETA", "0.5.7");
        ProjectVersion newer = version("0.9.0-Patch-1", "2026-07-02T12:00:00.123456789", "BETA", "0.5.7");

        assertEquals(newer, VersionSelector.latestCompatible(List.of(older, newer), "0.5.7").orElseThrow());
    }

    @Test
    void comparesLocalAndOffsetReleaseDatesOnTheSameTimeline() {
        ProjectVersion older = version("2.0", "2026-07-01T12:00:00+02:00", "RELEASE", "0.5.7");
        ProjectVersion newer = version("1.0", "2026-07-01T11:00:00", "RELEASE", "0.5.7");

        assertEquals(newer, VersionSelector.latestCompatible(List.of(older, newer), "0.5.7").orElseThrow());
    }
}
