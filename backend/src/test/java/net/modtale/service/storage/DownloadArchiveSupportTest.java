package net.modtale.service.storage;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DownloadArchiveSupportTest {

    private ProjectService projectService;
    private StorageService storageService;
    private AccessControlService accessControlService;
    private DownloadArchiveSupport service;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        storageService = mock(StorageService.class);
        accessControlService = mock(AccessControlService.class);
        service = new DownloadArchiveSupport(projectService, storageService, accessControlService);
    }

    @Test
    void resolveDependencyFindsMatchingProjectVersion() {
        Project project = project("project-1", version("1.0.0", "files/a.jar"));
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.isPubliclyReadable(project)).thenReturn(true);

        DownloadArchiveSupport.ResolvedDependency resolved =
                service.resolveDependency(new ProjectDependency("project-1", "Project", "1.0.0"));

        assertEquals(project, resolved.project());
        assertEquals("files/a.jar", resolved.version().getFileUrl());
        assertNull(service.resolveDependency(new ProjectDependency("missing", "Missing", "1.0.0")));
        assertNull(service.resolveDependency(new ProjectDependency("project-1", "Project", "2.0.0")));
    }

    @Test
    void withdrawnDependencyCannotBePackagedFromARawProject() {
        ProjectVersion withdrawn = version("1.0.0", "files/withdrawn.jar");
        withdrawn.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        Project project = project("project-1", withdrawn);
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.isPubliclyReadable(project)).thenReturn(true);

        assertNull(service.resolveDependency(new ProjectDependency("project-1", "Project", "1.0.0")));
    }

    @Test
    void privateDependencyCannotBePackagedFromARawProject() {
        Project project = project("project-1", version("1.0.0", "files/private.jar"));
        when(projectService.getRawProjectById("project-1")).thenReturn(project);

        assertNull(service.resolveDependency(new ProjectDependency("project-1", "Project", "1.0.0")));
    }

    @Test
    void approvedDependencyDownloadRejectsReplacedStorageBytes() throws Exception {
        ProjectVersion version = version("1.0.0", "files/plugin.jar");
        version.setHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("reviewed".getBytes(StandardCharsets.UTF_8))));
        when(storageService.downloadBounded("files/plugin.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES))
                .thenReturn("replaced".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class, () -> service.downloadApproved(version));
    }

    @Test
    void extractOriginalFilenameStripsStoragePrefixWhenPresent() {
        assertEquals("mod.jar", service.extractOriginalFilename("files/123456789012345678901234567890123456-mod.jar"));
        assertEquals("short.jar", service.extractOriginalFilename("files/short.jar"));
    }

    @Test
    void newZipMultipartFileExposesZipMetadataAndBytes() throws Exception {
        MultipartFile file = service.newZipMultipartFile("bundle.zip", "zip-bytes".getBytes(StandardCharsets.UTF_8));

        assertEquals("bundle.zip", file.getName());
        assertEquals("bundle.zip", file.getOriginalFilename());
        assertEquals("application/zip", file.getContentType());
        assertEquals(9, file.getSize());
        assertArrayEquals("zip-bytes".getBytes(StandardCharsets.UTF_8), file.getBytes());
        assertArrayEquals("zip-bytes".getBytes(StandardCharsets.UTF_8), file.getInputStream().readAllBytes());
    }

    private static Project project(String id, ProjectVersion... versions) {
        Project project = new Project();
        project.setId(id);
        project.setVersions(List.of(versions));
        return project;
    }

    private static ProjectVersion version(String versionNumber, String fileUrl) {
        ProjectVersion version = new ProjectVersion();
        version.setVersionNumber(versionNumber);
        version.setFileUrl(fileUrl);
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        return version;
    }
}
