package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.modtale.launcher.model.project.ProjectDependency;
import net.modtale.launcher.model.project.ProjectVersion;
import org.junit.jupiter.api.Test;

class NativeDependencyModalTest {

    @Test
    void selectableDependenciesMatchFrontendFilteringRules() {
        ProjectDependency required = dependency("required-api", "REQUIRED", "MODTALE");
        ProjectDependency optional = dependency("optional-map", "OPTIONAL", "MODTALE");
        ProjectDependency embedded = dependency("embedded-lib", "EMBEDDED", "MODTALE");
        ProjectDependency external = dependency("curse-lib", "REQUIRED", "CURSEFORGE");
        ProjectDependency malformed = dependency("", "REQUIRED", "MODTALE");

        ProjectVersion version = new ProjectVersion(
                "v1",
                "1.0.0",
                List.of("2026.06"),
                "/files/mod.jar",
                0,
                "2026-06-19T00:00:00Z",
                "",
                List.of(required, optional, embedded, external, malformed),
                "RELEASE"
        );

        assertEquals(List.of(required, optional), NativeDependencyModal.selectableDependencies(version));
    }

    private static ProjectDependency dependency(String projectId, String type, String source) {
        return new ProjectDependency(
                projectId + "-id",
                projectId,
                projectId,
                "1.0.0",
                type,
                source,
                "",
                "",
                "",
                "",
                "",
                true
        );
    }
}
