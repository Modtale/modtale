package net.modtale.controller.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.project.VersionService;
import net.modtale.service.storage.DownloadService;
import net.modtale.service.storage.DownloadTokenService;
import net.modtale.service.storage.StorageService;
import net.modtale.service.user.AccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionControllerTest {

    private VersionController controller;
    private VersionService versionService;
    private ProjectService projectService;
    private DownloadService downloadService;
    private DownloadTokenService downloadTokenService;
    private TrackingService trackingService;
    private StorageService storageService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        controller = new VersionController();
        versionService = mock(VersionService.class);
        projectService = mock(ProjectService.class);
        downloadService = mock(DownloadService.class);
        downloadTokenService = mock(DownloadTokenService.class);
        trackingService = mock(TrackingService.class);
        storageService = mock(StorageService.class);
        accountService = mock(AccountService.class);

        ReflectionTestUtils.setField(controller, "versionService", versionService);
        ReflectionTestUtils.setField(controller, "projectService", projectService);
        ReflectionTestUtils.setField(controller, "downloadService", downloadService);
        ReflectionTestUtils.setField(controller, "downloadTokenService", downloadTokenService);
        ReflectionTestUtils.setField(controller, "trackingService", trackingService);
        ReflectionTestUtils.setField(controller, "storageService", storageService);
        ReflectionTestUtils.setField(controller, "accountService", accountService);
        ReflectionTestUtils.setField(controller, "frontendUrl", "https://modtale.net");
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addVersionSplitsCommaSeparatedDependencyIdsBeforeDelegating() throws Exception {
        User currentUser = user("user-1");
        MockMultipartFile file = new MockMultipartFile("file", "mod.jar", "application/java-archive", new byte[]{1, 2, 3});

        when(accountService.getCurrentUser()).thenReturn(currentUser);

        var response = controller.addVersion(
                "project-1",
                "1.0.0",
                List.of("1.0.0"),
                file,
                List.of("dep-a, dep-b, , dep-c"),
                "Release notes",
                "beta"
        );

        assertEquals(200, response.getStatusCode().value());
        verify(versionService).addVersion(
                "project-1",
                "1.0.0",
                List.of("1.0.0"),
                file,
                "Release notes",
                List.of("dep-a", "dep-b", "dep-c"),
                ProjectVersion.Channel.BETA,
                currentUser
        );
    }

    @Test
    void downloadWithTokenReturnsGeneratedModpackZipAndTracksDependencies() throws Exception {
        User currentUser = user("user-1");
        Project project = project("project-1", "Sky Tools", ProjectClassification.MODPACK);
        ProjectVersion version = version("version-1", "1.0.0");
        version.setDependencies(List.of(new ProjectDependency("dep-1", "Dependency One", "2.0.0")));

        when(downloadTokenService.validateAndConsume("token")).thenReturn(
                new DownloadTokenService.DownloadToken("project-1", "1.0.0", "1.1.0", null, Instant.now().plusSeconds(60))
        );
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(versionService.findVersion(project, "1.0.0", "1.1.0")).thenReturn(version);
        when(accountService.getCurrentUser()).thenReturn(currentUser);
        when(downloadService.generateModpackZip(project, version, currentUser)).thenReturn(new byte[]{9, 8, 7});

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/download/token");
        request.addHeader("Referer", "https://modtale.net/mod/project-1");
        request.setRemoteAddr("198.51.100.8");

        var response = controller.downloadWithToken("token", request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("attachment; filename=\"Sky_Tools-1.0.0.zip\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));

        ByteArrayResource body = assertInstanceOf(ByteArrayResource.class, response.getBody());
        assertArrayEquals(new byte[]{9, 8, 7}, body.getByteArray());

        verify(trackingService).logDownload("project-1", "version-1", "Ada", false, "198.51.100.8");
        verify(trackingService).logDownload("dep-1", null, null, false, "198.51.100.8");
    }

    @Test
    void downloadWithTokenStripsGeneratedPrefixFromStoredFilenames() throws Exception {
        Project project = project("project-1", "Sky Tools", ProjectClassification.DATA);
        ProjectVersion version = version("version-1", "1.0.0");
        version.setFileUrl("https://cdn.modtale.net/files/123456789012345678901234567890123456-actual.jar");

        when(downloadTokenService.validateAndConsume("token")).thenReturn(
                new DownloadTokenService.DownloadToken("project-1", "1.0.0", null, null, Instant.now().plusSeconds(60))
        );
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(versionService.findVersion(project, "1.0.0", null)).thenReturn(version);
        when(storageService.download(version.getFileUrl())).thenReturn(new byte[]{1, 2, 3, 4});

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/download/token");
        request.addHeader("Referer", "https://modtale.net/mod/project-1");
        request.setRemoteAddr("203.0.113.5");

        var response = controller.downloadWithToken("token", request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("attachment; filename=\"actual.jar\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));

        ByteArrayResource body = assertInstanceOf(ByteArrayResource.class, response.getBody());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, body.getByteArray());

        verify(storageService).download(version.getFileUrl());
        verify(trackingService).logDownload("project-1", "version-1", "Ada", false, "203.0.113.5");
    }

    @Test
    void downloadBundleTracksOnlySelectedDependencies() throws Exception {
        User currentUser = user("user-1");
        Project project = project("project-1", "Sky Tools", ProjectClassification.MODPACK);
        ProjectVersion version = version("version-1", "1.0.0");
        version.setDependencies(List.of(
                new ProjectDependency("dep-a", "Dependency A", "1.0.0"),
                new ProjectDependency("dep-b", "Dependency B", "2.0.0"),
                new ProjectDependency("dep-c", "Dependency C", "3.0.0", false, true)
        ));

        when(downloadTokenService.validateAndConsume("bundle-token")).thenReturn(
                new DownloadTokenService.DownloadToken("project-1", "1.0.0", null, List.of("dep-b"), Instant.now().plusSeconds(60))
        );
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(versionService.findVersion(project, "1.0.0", null)).thenReturn(version);
        when(accountService.getCurrentUser()).thenReturn(currentUser);
        when(downloadService.generateBundleZip(project, version, List.of("dep-b"), currentUser)).thenReturn(new byte[]{6, 5, 4});

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/download-bundle/bundle-token");
        request.setRemoteAddr("192.0.2.11");

        var response = controller.downloadBundleWithToken("bundle-token", request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("attachment; filename=\"Sky_Tools-UNZIP-ME.zip\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));

        ByteArrayResource body = assertInstanceOf(ByteArrayResource.class, response.getBody());
        assertArrayEquals(new byte[]{6, 5, 4}, body.getByteArray());

        verify(trackingService).logDownload("project-1", "version-1", "Ada", true, "192.0.2.11");
        verify(trackingService).logDownload("dep-b", null, null, true, "192.0.2.11");
        verify(trackingService, never()).logDownload(eq("dep-a"), isNull(), isNull(), anyBoolean(), anyString());
        verify(trackingService, never()).logDownload(eq("dep-c"), isNull(), isNull(), anyBoolean(), anyString());
    }

    private static Project project(String id, String title, ProjectClassification classification) {
        Project project = new Project();
        project.setId(id);
        project.setTitle(title);
        project.setClassification(classification);
        project.setAuthor("Ada");
        project.setAuthorId("author-1");
        return project;
    }

    private static ProjectVersion version(String id, String versionNumber) {
        ProjectVersion version = new ProjectVersion();
        version.setId(id);
        version.setVersionNumber(versionNumber);
        return version;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
