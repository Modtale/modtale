package net.modtale.service.project.version;

import java.time.Instant;
import java.util.List;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.exception.InvalidDownloadTokenException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.exception.UnauthorizedException;
import net.modtale.model.dto.response.project.BundleDownloadUrlResponse;
import net.modtale.model.dto.response.project.DownloadUrlResponse;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.service.analytics.AnalyticsEligibilityService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.storage.DownloadService;
import net.modtale.service.storage.DownloadTokenService;
import net.modtale.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionDownloadOrchestrationServiceTest {

    private ProjectVersionAccessService projectVersionAccessService;
    private ProjectService projectService;
    private DownloadService downloadService;
    private DownloadTokenService downloadTokenService;
    private AnalyticsEligibilityService analyticsEligibilityService;
    private TrackingService trackingService;
    private StorageService storageService;
    private AccessControlService accessControlService;
    private VersionDownloadOrchestrationService service;

    @BeforeEach
    void setUp() {
        projectVersionAccessService = mock(ProjectVersionAccessService.class);
        projectService = mock(ProjectService.class);
        downloadService = mock(DownloadService.class);
        downloadTokenService = mock(DownloadTokenService.class);
        analyticsEligibilityService = mock(AnalyticsEligibilityService.class);
        trackingService = mock(TrackingService.class);
        storageService = mock(StorageService.class);
        accessControlService = mock(AccessControlService.class);
        service = new VersionDownloadOrchestrationService(
                projectVersionAccessService,
                projectService,
                downloadService,
                downloadTokenService,
                analyticsEligibilityService,
                trackingService,
                storageService,
                accessControlService,
                new AppFrontendProperties("https://modtale.test")
        );
    }

    @Test
    void createDownloadUrlAndBundleUrlGenerateShortLivedTokenRoutes() {
        User user = new User();
        user.setId("user-1");
        Project project = project("project-1", "Sky Tools", ProjectClassification.PLUGIN);
        ProjectVersion version = version("version-1", "1.0.0", "files/mod.jar");

        when(projectService.getProjectById("project-1", user)).thenReturn(project);
        when(projectVersionAccessService.requireByVersionNumber(org.mockito.Mockito.eq(project), org.mockito.Mockito.eq("1.0.0"), org.mockito.Mockito.eq("1.21.0"), org.mockito.Mockito.any()))
                .thenReturn(version);
        when(downloadTokenService.generateToken("project-1", "1.0.0", "1.21.0", null, "user-1")).thenReturn("download-token");
        when(downloadTokenService.generateToken("project-1", "1.0.0", "1.21.0", List.of("dep-1"), "user-1")).thenReturn("bundle-token");
        when(downloadTokenService.getTokenValiditySeconds()).thenReturn(300);

        DownloadUrlResponse download = service.createDownloadUrl("project-1", "1.0.0", "1.21.0", user);
        BundleDownloadUrlResponse bundle = service.createBundleDownloadUrl("project-1", "1.0.0", "1.21.0", List.of("dep-1"), user);

        assertEquals("/download/download-token", download.downloadUrl());
        assertEquals(300, download.expiresIn());
        assertEquals("/download-bundle/bundle-token", bundle.downloadUrl());
        assertEquals(300, bundle.expiresIn());
    }

    @Test
    void createDownloadUrlRejectsMissingProjects() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> service.createDownloadUrl("missing", "1.0.0", null, new User())
        );
    }

    @Test
    void downloadVersionConsumesTokenChecksReadAccessTracksAndReturnsStoredArtifact() throws Exception {
        User user = new User();
        Project project = project("project-1", "Sky Tools", ProjectClassification.PLUGIN);
        ProjectVersion version = version("version-1", "1.0.0", "files/123456789012345678901234567890123456-sky-tools.jar");
        DownloadTokenService.DownloadToken token = token("project-1", "1.0.0", "1.21.0", null);

        when(downloadTokenService.validateAndConsume("token")).thenReturn(token);
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.canReadProject(project, user)).thenReturn(true);
        when(projectVersionAccessService.requireByVersionNumber(org.mockito.Mockito.eq(project), org.mockito.Mockito.eq("1.0.0"), org.mockito.Mockito.eq("1.21.0"), org.mockito.Mockito.any()))
                .thenReturn(version);
        when(analyticsEligibilityService.shouldCountProjectEngagement(project, user)).thenReturn(true);
        when(storageService.download(version.getFileUrl())).thenReturn(new byte[]{1, 2, 3});

        VersionDownloadPayload payload = service.downloadVersion(
                "token",
                false,
                "https://modtale.test/project/sky-tools",
                "198.51.100.9",
                "203.0.113.1, 198.51.100.9",
                user
        );

        assertEquals("sky-tools.jar", payload.filename());
        assertArrayEquals(new byte[]{1, 2, 3}, payload.bytes());
        verify(trackingService).logDownload("project-1", "version-1", "author-name", false, "203.0.113.1");
    }

    @Test
    void downloadVersionGeneratesModpackZipAndTracksDependencies() throws Exception {
        User user = new User();
        Project pack = project("pack-1", "Sky Pack!", ProjectClassification.MODPACK);
        ProjectVersion version = version("version-1", "1.0.0", "modpacks/pack.zip");
        version.setDependencies(List.of(new ProjectDependency("dep-1", "Dependency", "2.0.0")));
        Project dependencyProject = project("dep-1", "Dependency", ProjectClassification.PLUGIN);

        when(downloadTokenService.validateAndConsume("token")).thenReturn(token("pack-1", "1.0.0", null, null));
        when(projectService.getRawProjectById("pack-1")).thenReturn(pack);
        when(projectService.getRawProjectById("dep-1")).thenReturn(dependencyProject);
        when(accessControlService.canReadProject(pack, user)).thenReturn(true);
        when(projectVersionAccessService.requireByVersionNumber(org.mockito.Mockito.eq(pack), org.mockito.Mockito.eq("1.0.0"), org.mockito.Mockito.isNull(), org.mockito.Mockito.any()))
                .thenReturn(version);
        when(analyticsEligibilityService.shouldCountProjectEngagement(pack, user)).thenReturn(true);
        when(analyticsEligibilityService.shouldCountProjectEngagement(dependencyProject, user)).thenReturn(true);
        when(downloadService.generateModpackZip(pack, version, user)).thenReturn(new byte[]{9, 8, 7});

        VersionDownloadPayload payload = service.downloadVersion("token", true, null, "198.51.100.9", null, user);

        assertEquals("Sky_Pack_-1.0.0.zip", payload.filename());
        assertArrayEquals(new byte[]{9, 8, 7}, payload.bytes());
        verify(trackingService).logDownload("pack-1", "version-1", "author-name", true, "198.51.100.9");
        verify(trackingService).logDownload("dep-1", null, "author-name", true, "198.51.100.9");
    }

    @Test
    void downloadBundleTracksOnlySelectedNonEmbeddedDependenciesAndReturnsZipName() throws Exception {
        User user = new User();
        Project project = project("project-1", "Sky Tools", ProjectClassification.PLUGIN);
        ProjectVersion version = version("version-1", "1.0.0", "files/mod.jar");
        version.setDependencies(List.of(
                new ProjectDependency("dep-1", "Dependency One", "1.0.0"),
                new ProjectDependency("dep-2", "Dependency Two", "1.0.0"),
                new ProjectDependency("embedded", "Embedded", "1.0.0", ProjectDependency.DependencyType.EMBEDDED)
        ));
        Project dependencyProject = project("dep-1", "Dependency One", ProjectClassification.DATA);

        when(downloadTokenService.validateAndConsume("token")).thenReturn(token("project-1", "1.0.0", null, List.of("dep-1")));
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(projectService.getRawProjectById("dep-1")).thenReturn(dependencyProject);
        when(accessControlService.canReadProject(project, user)).thenReturn(true);
        when(projectVersionAccessService.requireByVersionNumber(org.mockito.Mockito.eq(project), org.mockito.Mockito.eq("1.0.0"), org.mockito.Mockito.isNull(), org.mockito.Mockito.any()))
                .thenReturn(version);
        when(analyticsEligibilityService.shouldCountProjectEngagement(project, user)).thenReturn(true);
        when(analyticsEligibilityService.shouldCountProjectEngagement(dependencyProject, user)).thenReturn(true);
        when(downloadService.generateBundleZip(project, version, List.of("dep-1"), user)).thenReturn(new byte[]{4, 5});

        VersionDownloadPayload payload = service.downloadBundle("token", false, null, "198.51.100.9", null, user);

        assertEquals("Sky_Tools-UNZIP-ME.zip", payload.filename());
        assertArrayEquals(new byte[]{4, 5}, payload.bytes());
        verify(trackingService).logDownload("project-1", "version-1", "author-name", true, "198.51.100.9");
        verify(trackingService).logDownload("dep-1", null, "author-name", true, "198.51.100.9");
        verify(projectService, never()).getRawProjectById("dep-2");
        verify(projectService, never()).getRawProjectById("embedded");
    }

    @Test
    void downloadRejectsInvalidTokensOrUnreadableProjects() {
        User user = new User();
        Project project = project("project-1", "Sky Tools", ProjectClassification.PLUGIN);

        when(downloadTokenService.validateAndConsume("invalid")).thenReturn(null);
        when(downloadTokenService.validateAndConsume("unreadable")).thenReturn(token("project-1", "1.0.0", null, null));
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.canReadProject(project, user)).thenReturn(false);

        assertThrows(InvalidDownloadTokenException.class, () -> service.downloadVersion("invalid", false, null, null, null, user));
        assertThrows(ResourceNotFoundException.class, () -> service.downloadVersion("unreadable", false, null, null, null, user));
    }

    @Test
    void downloadRejectsUserBoundTokenWithoutMatchingSession() {
        User user = new User();
        user.setId("other-user");

        when(downloadTokenService.validateAndConsume("token")).thenReturn(
                new DownloadTokenService.DownloadToken(
                        "project-1",
                        "1.0.0",
                        null,
                        null,
                        "user-1",
                        Instant.now().plusSeconds(60)
                )
        );

        assertThrows(UnauthorizedException.class, () -> service.downloadVersion("token", false, null, null, null, user));
    }

    private static DownloadTokenService.DownloadToken token(
            String projectId,
            String version,
            String gameVersion,
            List<String> selectedDependencies
    ) {
        return new DownloadTokenService.DownloadToken(
                projectId,
                version,
                gameVersion,
                selectedDependencies,
                Instant.now().plusSeconds(60)
        );
    }

    private static Project project(String id, String title, ProjectClassification classification) {
        Project project = new Project();
        project.setId(id);
        project.setTitle(title);
        project.setAuthor("author-name");
        project.setClassification(classification);
        return project;
    }

    private static ProjectVersion version(String id, String versionNumber, String fileUrl) {
        ProjectVersion version = new ProjectVersion();
        version.setId(id);
        version.setVersionNumber(versionNumber);
        version.setFileUrl(fileUrl);
        return version;
    }
}
