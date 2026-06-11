package net.modtale.service.project;

import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.security.SanitizationService;
import net.modtale.service.security.ScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionServiceTest {

    private VersionService service;
    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private ProjectAccessService projectAccessService;
    private ProjectMutationGuard projectMutationGuard;
    private ProjectVersionAccessService projectVersionAccessService;
    private ValidationService validationService;
    private ScanService scanService;
    private SanitizationService sanitizationService;
    private AccessControlService accessControlService;
    private VersionArtifactService versionArtifactService;
    private VersionDependencyService versionDependencyService;
    private VersionManifestService versionManifestService;
    private ProjectDeletionService projectDeletionService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        accessControlService = mock(AccessControlService.class);
        projectAccessService = new ProjectAccessService(projectService, accessControlService);
        projectMutationGuard = new ProjectMutationGuard();
        validationService = mock(ValidationService.class);
        projectVersionAccessService = new ProjectVersionAccessService(validationService);
        scanService = mock(ScanService.class);
        sanitizationService = mock(SanitizationService.class);
        versionArtifactService = mock(VersionArtifactService.class);
        versionDependencyService = mock(VersionDependencyService.class);
        versionManifestService = mock(VersionManifestService.class);
        projectDeletionService = mock(ProjectDeletionService.class);
        VersionMutationOrchestrationService versionMutationOrchestrationService = new VersionMutationOrchestrationService(
                validationService,
                scanService,
                sanitizationService,
                versionArtifactService,
                versionDependencyService,
                projectDeletionService
        );
        VersionCreationCommandHandler versionCreationCommandHandler = new VersionCreationCommandHandler(
                projectRepository,
                projectService,
                projectAccessService,
                projectMutationGuard,
                versionMutationOrchestrationService,
                new AppLimitProperties(10, 5, 10, 5, 5, 50, 20, 10)
        );
        VersionUpdateCommandHandler versionUpdateCommandHandler = new VersionUpdateCommandHandler(
                projectRepository,
                projectService,
                projectAccessService,
                projectMutationGuard,
                projectVersionAccessService,
                versionMutationOrchestrationService
        );

        service = new VersionService(
                projectRepository,
                projectService,
                projectAccessService,
                projectMutationGuard,
                projectVersionAccessService,
                mock(MongoTemplate.class),
                versionManifestService,
                projectDeletionService,
                versionCreationCommandHandler,
                versionUpdateCommandHandler
        );
    }

    @Test
    void addVersionQueuesInitialScanForNonModpackArtifacts() throws Exception {
        Project project = new Project();
        project.setId("project-1");
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setClassification(ProjectClassification.DATA);
        project.setVersions(new ArrayList<>());

        User user = new User();
        user.setId("user-1");

        MockMultipartFile file = new MockMultipartFile("file", "bundle.zip", "application/zip", new byte[]{1, 2, 3});
        ScanResult queuedScan = new ScanResult();

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "VERSION_CREATE")).thenReturn(true);
        when(sanitizationService.sanitizePlainText("Release notes")).thenReturn("Release notes");
        doNothing().when(validationService).validateVersionNumber("1.0.0");
        when(validationService.getAllowedGameVersions()).thenReturn(List.of("1.21.0"));
        when(versionArtifactService.prepareVersionArtifact(project, file))
                .thenReturn(new VersionArtifactService.PreparedVersionArtifact(ProjectClassification.DATA, "/files/data/bundle.zip", "sha-256"));
        when(scanService.createQueuedScanResult(1, "Initial scan queued.")).thenReturn(queuedScan);
        when(versionDependencyService.resolveRequestedDependencies(List.of("dep-1:2.0.0"), false, false))
                .thenReturn(new VersionDependencyService.ResolvedDependencies(List.of(), List.of("dep-1")));

        service.addVersion(
                "project-1",
                "1.0.0",
                List.of("1.21.0"),
                file,
                "Release notes",
                List.of("dep-1:2.0.0"),
                ProjectVersion.Channel.RELEASE,
                user
        );

        ProjectVersion savedVersion = project.getVersions().getFirst();
        assertEquals("/files/data/bundle.zip", savedVersion.getFileUrl());
        assertEquals("sha-256", savedVersion.getHash());
        assertEquals(queuedScan, savedVersion.getScanResult());
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        verify(scanService).enqueueBackgroundScan("project-1", savedVersion.getId(), "/files/data/bundle.zip", "bundle.zip", false, 1);
    }

    @Test
    void addVersionDoesNotQueueScanForDraftArtifacts() throws Exception {
        Project project = new Project();
        project.setId("project-1");
        project.setStatus(ProjectStatus.DRAFT);
        project.setClassification(ProjectClassification.DATA);
        project.setVersions(new ArrayList<>());

        User user = new User();
        user.setId("user-1");

        MockMultipartFile file = new MockMultipartFile("file", "bundle.zip", "application/zip", new byte[]{1, 2, 3});

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "VERSION_CREATE")).thenReturn(true);
        when(sanitizationService.sanitizePlainText("Release notes")).thenReturn("Release notes");
        doNothing().when(validationService).validateVersionNumber("1.0.0");
        when(validationService.getAllowedGameVersions()).thenReturn(List.of("1.21.0"));
        when(versionArtifactService.prepareVersionArtifact(project, file))
                .thenReturn(new VersionArtifactService.PreparedVersionArtifact(ProjectClassification.DATA, "/files/data/bundle.zip", "sha-256"));

        service.addVersion(
                "project-1",
                "1.0.0",
                List.of("1.21.0"),
                file,
                "Release notes",
                null,
                ProjectVersion.Channel.RELEASE,
                user
        );

        ProjectVersion savedVersion = project.getVersions().getFirst();
        assertNull(savedVersion.getScanResult());
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        verify(scanService, never()).createQueuedScanResult(1, "Initial scan queued.");
        verify(scanService, never()).enqueueBackgroundScan("project-1", savedVersion.getId(), "/files/data/bundle.zip", "bundle.zip", false, 1);
    }

    @Test
    void updateVersionClearsCachedModpackArchivesAndRefreshesLatestDependencyIds() {
        Project project = new Project();
        project.setId("project-1");
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setClassification(ProjectClassification.MODPACK);
        project.setModIds(new ArrayList<>(List.of("old-dep")));

        ProjectVersion version = new ProjectVersion();
        version.setId("version-1");
        version.setFileUrl("modpacks/sky-pack-1.0.0.zip");
        version.setDependencies(new ArrayList<>(List.of(new ProjectDependency("old-dep", "Old Dependency", "1.0.0"))));
        project.setVersions(new ArrayList<>(List.of(version)));

        User user = new User();
        user.setId("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "VERSION_EDIT")).thenReturn(true);
        when(versionDependencyService.resolveRequestedDependencies(List.of("new-dep:2.0.0"), true, true))
                .thenReturn(new VersionDependencyService.ResolvedDependencies(
                        List.of(new ProjectDependency("new-dep", "New Dependency", "2.0.0")),
                        List.of("new-dep")
                ));

        service.updateVersion(
                "project-1",
                "version-1",
                List.of("new-dep:2.0.0"),
                null,
                null,
                null,
                user
        );

        assertNull(version.getFileUrl());
        assertEquals(List.of("new-dep"), project.getModIds());
        assertEquals("new-dep", version.getDependencies().getFirst().getModId());
        verify(projectDeletionService).deleteStoredFile("modpacks/sky-pack-1.0.0.zip");
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
    }

    @Test
    void deleteVersionAllowsPrivateProjectsToRemoveTheirLastVersion() {
        Project project = new Project();
        project.setId("project-1");
        project.setStatus(ProjectStatus.PRIVATE);

        ProjectVersion version = new ProjectVersion();
        version.setId("version-1");
        version.setFileUrl("/files/private/bundle.zip");
        project.setVersions(new ArrayList<>(List.of(version)));

        User user = new User();
        user.setId("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "VERSION_DELETE")).thenReturn(true);

        service.deleteVersion("project-1", "version-1", user);

        assertEquals(0, project.getVersions().size());
        verify(projectDeletionService).deleteVersionFile(version);
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
    }

    @Test
    void deleteVersionStillProtectsTheLastVersionForPublishedProjects() {
        Project project = new Project();
        project.setId("project-1");
        project.setStatus(ProjectStatus.PUBLISHED);

        ProjectVersion version = new ProjectVersion();
        version.setId("version-1");
        version.setFileUrl("/files/public/bundle.zip");
        project.setVersions(new ArrayList<>(List.of(version)));

        User user = new User();
        user.setId("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "VERSION_DELETE")).thenReturn(true);

        assertThrows(InvalidVersionRequestException.class, () -> service.deleteVersion("project-1", "version-1", user));
        verify(projectDeletionService, never()).deleteVersionFile(version);
    }
}
