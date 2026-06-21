package net.modtale.service.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.modtale.exception.StorageDownloadException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.repository.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModpackArchiveServiceTest {

    private ProjectRepository projectRepository;
    private DownloadArchiveSupport archiveSupport;
    private ModpackArchiveService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        archiveSupport = mock(DownloadArchiveSupport.class);
        service = new ModpackArchiveService(projectRepository, archiveSupport);
    }

    @Test
    void generateModpackZipReturnsCachedArchiveWhenDownloadSucceeds() throws Exception {
        Project pack = pack();
        ProjectVersion version = version("1.0.0", "modpacks/cached.zip");

        when(archiveSupport.download("modpacks/cached.zip")).thenReturn(new byte[]{1, 2, 3});

        assertArrayEquals(new byte[]{1, 2, 3}, service.generateModpackZip(pack, version));
        verify(projectRepository, never()).save(pack);
    }

    @Test
    void generateModpackZipRebuildsCachesAndOrganizesDependencyFilesByClassification() throws Exception {
        Project pack = pack();
        ProjectVersion version = version("1.0.0", "modpacks/missing.zip");
        version.setDependencies(List.of(
                new ProjectDependency("plugin", "Plugin", "2.0.0"),
                new ProjectDependency("data", "Data", "3.0.0")
        ));
        Project plugin = dependencyProject("plugin", ProjectClassification.PLUGIN);
        Project data = dependencyProject("data", ProjectClassification.DATA);
        ProjectVersion pluginVersion = version("2.0.0", "files/plugin.jar");
        ProjectVersion dataVersion = version("3.0.0", "files/data.zip");

        when(archiveSupport.download("modpacks/missing.zip"))
                .thenThrow(new StorageDownloadException("missing", new IOException("missing")));
        when(archiveSupport.resolveDependency(version.getDependencies().getFirst()))
                .thenReturn(new DownloadArchiveSupport.ResolvedDependency(plugin, pluginVersion));
        when(archiveSupport.resolveDependency(version.getDependencies().get(1)))
                .thenReturn(new DownloadArchiveSupport.ResolvedDependency(data, dataVersion));
        when(archiveSupport.download("files/plugin.jar")).thenReturn(bytes("plugin-binary"));
        when(archiveSupport.download("files/data.zip")).thenReturn(bytes("data-binary"));
        when(archiveSupport.extractOriginalFilename("files/plugin.jar")).thenReturn("plugin.jar");
        when(archiveSupport.extractOriginalFilename("files/data.zip")).thenReturn("data.zip");
        when(archiveSupport.newZipMultipartFile(eq("sky-pack-1.0.0.zip"), any())).thenAnswer(invocation -> mock(MultipartFile.class));
        when(archiveSupport.upload(any(MultipartFile.class), eq("modpacks"))).thenReturn("modpacks/generated.zip");

        Map<String, String> entries = unzip(service.generateModpackZip(pack, version));

        assertEquals("modpacks/generated.zip", version.getFileUrl());
        assertEquals("plugin-binary", entries.get("plugins/plugin.jar"));
        assertEquals("data-binary", entries.get("asset-packs/data.zip"));
        assertEquals(true, entries.get("modpack.json").contains("\"id\": \"plugin\""));
        verify(projectRepository).save(pack);
    }

    private static Project pack() {
        Project project = new Project();
        project.setId("pack-1");
        project.setSlug("sky-pack");
        project.setTitle("Sky Pack");
        project.setClassification(ProjectClassification.MODPACK);
        return project;
    }

    private static Project dependencyProject(String id, ProjectClassification classification) {
        Project project = new Project();
        project.setId(id);
        project.setClassification(classification);
        return project;
    }

    private static ProjectVersion version(String versionNumber, String fileUrl) {
        ProjectVersion version = new ProjectVersion();
        version.setVersionNumber(versionNumber);
        version.setFileUrl(fileUrl);
        return version;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
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
