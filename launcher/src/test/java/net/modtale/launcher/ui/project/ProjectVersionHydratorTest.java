package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.model.project.ProjectVersion;
import org.junit.jupiter.api.Test;

class ProjectVersionHydratorTest {

    @Test
    void hydratesEmptyDetailVersionsFromVersionsEndpoint() {
        ProjectVersion loaded = version("1.0.0");
        AtomicReference<String> requestedKey = new AtomicReference<>();

        ProjectDetail hydrated = ProjectVersionHydrator.hydrate(
                detail(List.of()),
                summary(List.of()),
                key -> {
                    requestedKey.set(key);
                    return List.of(loaded);
                }
        );

        assertEquals("levelingcore", requestedKey.get());
        assertEquals(List.of(loaded), hydrated.versions());
    }

    @Test
    void keepsDetailVersionsWhenAlreadyPresent() {
        ProjectVersion existing = version("1.0.0");
        ProjectDetail detail = detail(List.of(existing));
        AtomicBoolean loaded = new AtomicBoolean(false);

        ProjectDetail hydrated = ProjectVersionHydrator.hydrate(
                detail,
                summary(List.of()),
                key -> {
                    loaded.set(true);
                    return List.of(version("1.1.0"));
                }
        );

        assertSame(detail, hydrated);
        assertEquals(false, loaded.get());
    }

    @Test
    void fallsBackToSummaryVersionsWhenEndpointHasNone() {
        ProjectVersion summaryVersion = version("1.0.0");

        ProjectDetail hydrated = ProjectVersionHydrator.hydrate(
                detail(List.of()),
                summary(List.of(summaryVersion)),
                key -> List.of()
        );

        assertEquals(List.of(summaryVersion), hydrated.versions());
    }

    @Test
    void fallsBackToSummaryVersionsWhenEndpointFails() {
        ProjectVersion summaryVersion = version("1.0.0");

        ProjectDetail hydrated = ProjectVersionHydrator.hydrate(
                detail(List.of()),
                summary(List.of(summaryVersion)),
                key -> {
                    throw new IllegalStateException("offline");
                }
        );

        assertEquals(List.of(summaryVersion), hydrated.versions());
    }

    @Test
    void installPathRethrowsWhenNoFallbackVersionsExist() {
        ProjectDetail detail = detail(List.of());

        assertThrows(IllegalStateException.class, () -> ProjectVersionHydrator.hydrateOrThrow(
                detail,
                summary(List.of()),
                key -> {
                    throw new IllegalStateException("offline");
                }
        ));
    }

    private static ProjectSummary summary(List<ProjectVersion> versions) {
        return new ProjectSummary(
                "project-1",
                "levelingcore",
                "LevelingCore",
                "Core leveling APIs",
                "author-1",
                "AzureDoom",
                null,
                null,
                "PLUGIN",
                0,
                0,
                "2026-01-01T00:00:00Z",
                versions
        );
    }

    private static ProjectDetail detail(List<ProjectVersion> versions) {
        return new ProjectDetail(
                "project-1",
                "levelingcore",
                "LevelingCore",
                "Core leveling APIs",
                "AzureDoom",
                "PLUGIN",
                "2026-01-01T00:00:00Z",
                "MIT",
                "",
                List.of(),
                versions
        );
    }

    private static ProjectVersion version(String versionNumber) {
        return new ProjectVersion(
                versionNumber,
                versionNumber,
                List.of("2026.1"),
                null,
                0,
                "2026-01-01T00:00:00Z",
                "",
                List.of(),
                "RELEASE"
        );
    }
}
