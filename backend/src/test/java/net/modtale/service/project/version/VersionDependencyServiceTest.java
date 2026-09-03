package net.modtale.service.project.version;

import java.util.List;
import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.model.dto.request.project.DependencyReferenceRequest;
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
                List.of(
                        dependency("dep-1", "1.0.0", ProjectDependency.DependencyType.OPTIONAL),
                        dependency("dep-2", "2.0.0", ProjectDependency.DependencyType.EMBEDDED)
                ),
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
                () -> service.resolveRequestedDependencies(List.of(dependency("dep-1", "1.0.0")), true, false)
        );
    }

    @Test
    void resolveRequestedDependenciesPreservesOptionalTypesForModpacks() {
        when(projectService.getRawProjectById("first-mod"))
                .thenReturn(project("first-mod", "First Mod", ProjectStatus.PUBLISHED, "1.0.0"));
        when(projectService.getRawProjectById("second-mod"))
                .thenReturn(project("second-mod", "Second Mod", ProjectStatus.PUBLISHED, "2.0.0"));
        DependencyReferenceRequest first = dependency(
                "first-mod",
                "1.0.0",
                ProjectDependency.DependencyType.OPTIONAL
        );
        DependencyReferenceRequest second = dependency(
                "second-mod",
                "2.0.0",
                ProjectDependency.DependencyType.REQUIRED
        );

        VersionDependencyService.ResolvedDependencies resolved = service.resolveRequestedDependencies(
                List.of(first, second),
                true,
                false
        );

        assertEquals(ProjectDependency.DependencyType.OPTIONAL,
                resolved.dependencies().getFirst().getDependencyType());
        assertEquals(ProjectDependency.DependencyType.REQUIRED,
                resolved.dependencies().get(1).getDependencyType());
    }

    @Test
    void resolveRequestedDependenciesRejectsDuplicateProjects() {
        when(projectService.getRawProjectById("dep-1")).thenReturn(project("dep-1", "Dependency One", ProjectStatus.PUBLISHED, "1.0.0", "2.0.0"));

        InvalidVersionRequestException error = assertThrows(
                InvalidVersionRequestException.class,
                () -> service.resolveRequestedDependencies(
                        List.of(dependency("dep-1", "1.0.0"), dependency("dep-1", "2.0.0")),
                        true,
                        false
                )
        );

        assertEquals("Each dependency can only be included once.", error.getMessage());
    }

    @Test
    void resolveRequestedDependenciesRejectsMalformedMissingDraftOrUnknownVersions() {
        when(projectService.getRawProjectById("draft")).thenReturn(project("draft", "Draft", ProjectStatus.DRAFT, "1.0.0"));
        when(projectService.getRawProjectById("dep-1")).thenReturn(project("dep-1", "Dependency One", ProjectStatus.PUBLISHED, "1.0.0"));

        assertThrows(InvalidVersionRequestException.class, () -> service.resolveRequestedDependencies(List.of(new DependencyReferenceRequest()), false, false));
        assertThrows(InvalidVersionRequestException.class, () -> service.resolveRequestedDependencies(List.of(dependency("missing", "1.0.0")), false, false));
        assertThrows(InvalidVersionRequestException.class, () -> service.resolveRequestedDependencies(List.of(dependency("draft", "1.0.0")), false, false));
        assertThrows(InvalidVersionRequestException.class, () -> service.resolveRequestedDependencies(List.of(dependency("dep-1", "2.0.0")), false, false));
    }

    @Test
    void resolveRequestedDependenciesAllowsDraftsWhenRequested() {
        when(projectService.getRawProjectById("draft")).thenReturn(project("draft", "Draft", ProjectStatus.DRAFT, "1.0.0"));

        VersionDependencyService.ResolvedDependencies resolved =
                service.resolveRequestedDependencies(List.of(dependency("draft", "1.0.0")), false, true);

        assertEquals("draft", resolved.dependencies().getFirst().getProjectId());
    }

    @Test
    void resolveRequestedDependenciesAcceptsConfirmedExternalReferencesWithFileMetadata() {
        DependencyReferenceRequest external = new DependencyReferenceRequest();
        external.setSource(ProjectDependency.Source.GITHUB);
        external.setProjectTitle("GitHub Mod");
        external.setVersionNumber("latest");
        external.setExternalUrl("https://github.com/modtale/example-mod");
        external.setExternalFileUrl("https://raw.githubusercontent.com/modtale/example-mod/main/build/libs/example-mod.jar");
        external.setExternalFileName("example-mod.jar");
        external.setHytaleProjectConfirmed(true);

        VersionDependencyService.ResolvedDependencies resolved =
                service.resolveRequestedDependencies(List.of(external), false, false);

        ProjectDependency dependency = resolved.dependencies().getFirst();
        assertTrue(dependency.isExternal());
        assertEquals(ProjectDependency.Source.GITHUB, dependency.getSource());
        assertEquals("example-mod.jar", dependency.getExternalFileName());
        assertEquals("https://raw.githubusercontent.com/modtale/example-mod/main/build/libs/example-mod.jar", dependency.getExternalFileUrl());
    }

    @Test
    void resolveRequestedDependenciesRequiresSecureCanonicalCurseForgeUrls() {
        DependencyReferenceRequest valid = curseForgeDependency(
                "https://www.curseforge.com/hytale/mods/simple-compost/files/8227810"
        );

        ProjectDependency resolved = service.resolveRequestedDependencies(List.of(valid), false, false)
                .dependencies().getFirst();
        assertTrue(resolved.isHytaleProjectConfirmed());

        for (String invalidUrl : List.of(
                "http://www.curseforge.com/hytale/mods/simple-compost/files/8227810",
                "https://attacker@www.curseforge.com/hytale/mods/simple-compost/files/8227810",
                "https://curseforge.com.evil.example/hytale/mods/simple-compost/files/8227810",
                "https://www.curseforge.com/minecraft/mc-mods/simple-compost/files/8227810"
        )) {
            assertThrows(InvalidVersionRequestException.class, () ->
                    service.resolveRequestedDependencies(List.of(curseForgeDependency(invalidUrl)), false, false));
        }
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

    private static DependencyReferenceRequest dependency(String projectId, String versionNumber) {
        return dependency(projectId, versionNumber, ProjectDependency.DependencyType.REQUIRED);
    }

    private static DependencyReferenceRequest dependency(
            String projectId,
            String versionNumber,
            ProjectDependency.DependencyType dependencyType
    ) {
        DependencyReferenceRequest request = new DependencyReferenceRequest();
        request.setProjectId(projectId);
        request.setVersionNumber(versionNumber);
        request.setDependencyType(dependencyType);
        return request;
    }

    private static DependencyReferenceRequest curseForgeDependency(String url) {
        DependencyReferenceRequest request = new DependencyReferenceRequest();
        request.setSource(ProjectDependency.Source.CURSEFORGE);
        request.setProjectTitle("Simple Compost");
        request.setVersionNumber("1.0.0");
        request.setExternalUrl(url);
        request.setExternalFileUrl(url);
        return request;
    }
}
