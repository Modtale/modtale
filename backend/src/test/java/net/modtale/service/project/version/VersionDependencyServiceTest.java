package net.modtale.service.project.version;

import java.util.List;
import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VersionDependencyServiceTest {

    private ProjectService projectService;
    private VersionDependencyService service;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        service = new VersionDependencyService(projectService);
    }

    @Test
    void resolveRequestedDependenciesBuildsDependencyModelsAndFlags() {
        when(projectService.getRawProjectById("dep-1")).thenReturn(project("dep-1", "Dependency One", ProjectStatus.PUBLISHED, "1.0.0"));
        when(projectService.getRawProjectById("dep-2")).thenReturn(project("dep-2", "Dependency Two", ProjectStatus.PUBLISHED, "2.0.0"));

        VersionDependencyService.ResolvedDependencies resolved = service.resolveRequestedDependencies(
                List.of("dep-1:1.0.0:optional", "dep-2:2.0.0:embedded"),
                false,
                false
        );

        assertEquals(List.of("dep-1", "dep-2"), resolved.simpleProjectIds());
        ProjectDependency first = resolved.dependencies().getFirst();
        ProjectDependency second = resolved.dependencies().get(1);
        assertTrue(first.isOptional());
        assertFalse(first.isEmbedded());
        assertFalse(second.isOptional());
        assertTrue(second.isEmbedded());
    }

    @Test
    void resolveRequestedDependenciesRequiresAtLeastTwoDependenciesForModpacks() {
        when(projectService.getRawProjectById("dep-1")).thenReturn(project("dep-1", "Dependency One", ProjectStatus.PUBLISHED, "1.0.0"));

        assertThrows(
                InvalidVersionRequestException.class,
                () -> service.resolveRequestedDependencies(List.of("dep-1:1.0.0"), true, false)
        );
    }

    @Test
    void resolveRequestedDependenciesRejectsMalformedMissingDraftOrUnknownVersions() {
        when(projectService.getRawProjectById("draft")).thenReturn(project("draft", "Draft", ProjectStatus.DRAFT, "1.0.0"));
        when(projectService.getRawProjectById("dep-1")).thenReturn(project("dep-1", "Dependency One", ProjectStatus.PUBLISHED, "1.0.0"));

        assertThrows(InvalidVersionRequestException.class, () -> service.resolveRequestedDependencies(List.of("bad-entry"), false, false));
        assertThrows(InvalidVersionRequestException.class, () -> service.resolveRequestedDependencies(List.of("missing:1.0.0"), false, false));
        assertThrows(InvalidVersionRequestException.class, () -> service.resolveRequestedDependencies(List.of("draft:1.0.0"), false, false));
        assertThrows(InvalidVersionRequestException.class, () -> service.resolveRequestedDependencies(List.of("dep-1:2.0.0"), false, false));
    }

    @Test
    void resolveRequestedDependenciesAllowsDraftsWhenRequested() {
        when(projectService.getRawProjectById("draft")).thenReturn(project("draft", "Draft", ProjectStatus.DRAFT, "1.0.0"));

        VersionDependencyService.ResolvedDependencies resolved =
                service.resolveRequestedDependencies(List.of("draft:1.0.0"), false, true);

        assertEquals("draft", resolved.dependencies().getFirst().getModId());
    }

    @Test
    void resolveRequestedProjectIdsTrimsBlanksAndCanRejectDrafts() {
        when(projectService.getRawProjectById("dep-1")).thenReturn(project("dep-1", "Dependency One", ProjectStatus.PUBLISHED, "1.0.0"));
        when(projectService.getRawProjectById("draft")).thenReturn(project("draft", "Draft", ProjectStatus.DRAFT, "1.0.0"));

        assertEquals(List.of("dep-1"), service.resolveRequestedProjectIds(List.of(" dep-1 ", "", " "), false));
        assertThrows(
                InvalidVersionRequestException.class,
                () -> service.resolveRequestedProjectIds(List.of("draft"), false)
        );
        assertEquals(List.of("draft"), service.resolveRequestedProjectIds(List.of("draft"), true));
    }

    private static Project project(String id, String title, ProjectStatus status, String... versions) {
        Project project = new Project();
        project.setId(id);
        project.setTitle(title);
        project.setStatus(status);
        project.setVersions(java.util.Arrays.stream(versions).map(versionNumber -> {
            ProjectVersion version = new ProjectVersion();
            version.setVersionNumber(versionNumber);
            return version;
        }).toList());
        return project;
    }
}
