package net.modtale.service.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.RateLimitExceededException;
import net.modtale.exception.StorageDownloadException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DownloadServiceTest {

    private DownloadService downloadService;
    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        storageService = mock(StorageService.class);
        downloadService = new DownloadService(projectRepository, projectService, storageService, limitProperties(10));
    }

    @Test
    void generateModpackZipReturnsTheStoredArchiveWhenItAlreadyExists() throws Exception {
        Project pack = pack("pack-1", "sky-pack", "Sky Pack");
        ProjectVersion version = version("1.0.0");
        version.setFileUrl("modpacks/already-built.zip");
        User user = user("user-1");

        when(storageService.download("modpacks/already-built.zip")).thenReturn(new byte[]{1, 2, 3});

        byte[] zipBytes = downloadService.generateModpackZip(pack, version, user);

        assertArrayEquals(new byte[]{1, 2, 3}, zipBytes);
        verify(storageService).download("modpacks/already-built.zip");
        verifyNoInteractions(projectService, projectRepository);
    }

    @Test
    void generateModpackZipBuildsAndUploadsANewArchiveWhenTheCachedFileIsMissing() throws Exception {
        Project pack = pack("pack-1", "sky-pack", "Sky Pack");
        ProjectVersion version = version("1.0.0");
        version.setFileUrl("modpacks/missing.zip");
        version.setDependencies(List.of(
                new ProjectDependency("plugin-1", "Sky Plugin", "2.0.0"),
                new ProjectDependency("asset-1", "Sky Assets", "3.0.0")
        ));
        pack.setVersions(List.of(version));

        Project pluginProject = dependencyProject("plugin-1", ProjectClassification.PLUGIN, "2.0.0", "files/123456789012345678901234567890123456-plugin.jar");
        Project assetProject = dependencyProject("asset-1", ProjectClassification.DATA, "3.0.0", "files/123456789012345678901234567890123456-assets.zip");

        when(storageService.download("modpacks/missing.zip"))
                .thenThrow(new StorageDownloadException("missing", new IOException("missing")));
        when(projectService.getRawProjectById("plugin-1")).thenReturn(pluginProject);
        when(projectService.getRawProjectById("asset-1")).thenReturn(assetProject);
        when(storageService.download("files/123456789012345678901234567890123456-plugin.jar"))
                .thenReturn("plugin-binary".getBytes(StandardCharsets.UTF_8));
        when(storageService.download("files/123456789012345678901234567890123456-assets.zip"))
                .thenReturn("asset-binary".getBytes(StandardCharsets.UTF_8));
        when(storageService.upload(any(MultipartFile.class), eq("modpacks"))).thenReturn("modpacks/generated.zip");

        byte[] zipBytes = downloadService.generateModpackZip(pack, version, user("user-1"));

        Map<String, String> entries = unzip(zipBytes);
        assertTrue(entries.containsKey("modpack.json"));
        assertTrue(entries.get("modpack.json").contains("\"id\": \"plugin-1\""));
        assertTrue(entries.get("modpack.json").contains("\"version\": \"3.0.0\""));
        assertEquals("plugin-binary", entries.get("plugins/plugin.jar"));
        assertEquals("asset-binary", entries.get("asset-packs/assets.zip"));
        assertEquals("modpacks/generated.zip", version.getFileUrl());

        ArgumentCaptor<MultipartFile> uploadCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(storageService).upload(uploadCaptor.capture(), eq("modpacks"));
        assertEquals("sky-pack-1.0.0.zip", uploadCaptor.getValue().getOriginalFilename());
        verify(projectRepository).save(pack);
    }

    @Test
    void generateModpackZipAppliesPerUserRateLimiting() throws Exception {
        downloadService = new DownloadService(projectRepository, projectService, storageService, limitProperties(1));

        Project pack = pack("pack-1", "tiny-pack", "Tiny Pack");
        ProjectVersion version = version("1.0.0");
        User user = user("user-1");

        downloadService.generateModpackZip(pack, version, user);

        RateLimitExceededException error = assertThrows(
                RateLimitExceededException.class,
                () -> downloadService.generateModpackZip(pack, version, user)
        );

        assertTrue(error.getMessage().contains("Modpack generation limit reached"));
    }

    @Test
    void generateBundleZipIncludesTheMainFileAndOnlySelectedDependencies() throws Exception {
        Project mainProject = pack("pack-1", "sky-pack", "Sky Pack");
        ProjectVersion mainVersion = version("1.0.0");
        mainVersion.setFileUrl("files/123456789012345678901234567890123456-main.jar");
        mainVersion.setDependencies(List.of(
                new ProjectDependency("dep-a", "Dependency A", "1.0.0"),
                new ProjectDependency("dep-b", "Dependency B", "2.0.0"),
                new ProjectDependency("dep-c", "Dependency C", "3.0.0", ProjectDependency.DependencyType.EMBEDDED)
        ));

        Project dependencyB = dependencyProject("dep-b", ProjectClassification.DATA, "2.0.0", "files/123456789012345678901234567890123456-depb.jar");

        when(storageService.download("files/123456789012345678901234567890123456-main.jar"))
                .thenReturn("main-binary".getBytes(StandardCharsets.UTF_8));
        when(projectService.getRawProjectById("dep-b")).thenReturn(dependencyB);
        when(storageService.download("files/123456789012345678901234567890123456-depb.jar"))
                .thenReturn("depb-binary".getBytes(StandardCharsets.UTF_8));

        byte[] zipBytes = downloadService.generateBundleZip(mainProject, mainVersion, List.of("dep-b"), user("user-1"));

        Map<String, String> entries = unzip(zipBytes);
        assertEquals(Map.of(
                "main.jar", "main-binary",
                "depb.jar", "depb-binary"
        ), entries);
        verify(projectService, never()).getRawProjectById("dep-a");
        verify(projectService, never()).getRawProjectById("dep-c");
    }

    private static Project pack(String id, String slug, String title) {
        Project project = new Project();
        project.setId(id);
        project.setSlug(slug);
        project.setTitle(title);
        project.setClassification(ProjectClassification.MODPACK);
        return project;
    }

    private static ProjectVersion version(String versionNumber) {
        ProjectVersion version = new ProjectVersion();
        version.setVersionNumber(versionNumber);
        return version;
    }

    private static Project dependencyProject(String id, ProjectClassification classification, String versionNumber, String fileUrl) {
        Project project = new Project();
        project.setId(id);
        project.setClassification(classification);

        ProjectVersion version = new ProjectVersion();
        version.setVersionNumber(versionNumber);
        version.setFileUrl(fileUrl);
        project.setVersions(List.of(version));
        return project;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static AppLimitProperties limitProperties(int modpackGenPerHour) {
        return new AppLimitProperties(10, 5, 10, 5, 5, 50, 20, modpackGenPerHour);
    }

    private static Map<String, String> unzip(byte[] zipBytes) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }
        return entries;
    }
}
