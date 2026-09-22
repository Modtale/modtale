package net.modtale.service.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.modtale.model.dto.request.project.DependencyReferenceRequest;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.project.version.ExternalDependencyArtifactService;
import net.modtale.service.project.version.VersionDependencyService;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurseForgeModpackFlowTest {

    @Test
    void legacyCurseForgePackDataCanOnlyBePackagedAsAnAttributableReference() throws Exception {
        ProjectService projectService = mock(ProjectService.class);
        Project hostedProject = hostedProject();
        when(projectService.getRawProjectById("hosted-mod")).thenReturn(hostedProject);

        VersionDependencyService dependencyService = new VersionDependencyService(projectService);
        VersionDependencyService.ResolvedDependencies resolved = dependencyService.resolveRequestedDependencies(
                List.of(hostedReference(), curseForgeReference()),
                false,
                false
        );
        new ExternalDependencyArtifactService().prepareExternalArtifacts(resolved.dependencies());

        ProjectDependency curseForge = resolved.dependencies().get(1);
        assertEquals(ProjectDependency.Source.CURSEFORGE, curseForge.getSource());
        assertEquals("https://www.curseforge.com/hytale/mods/simple-compost/files/8227810", curseForge.getExternalFileUrl());
        assertNull(curseForge.getCachedFileUrl());
        curseForge.setExternalFileSize(4096L);
        curseForge.setExternalFileHashes(Map.of("sha1", "a".repeat(40), "md5", "b".repeat(32)));
        curseForge.setExternalGameVersions(List.of("2026.09"));
        curseForge.setExternalFileStatus(4);
        curseForge.setExternalDistributionAllowed(false);

        ProjectRepository projectRepository = mock(ProjectRepository.class);
        DownloadArchiveSupport archiveSupport = mock(DownloadArchiveSupport.class);
        ProjectVersion packVersion = new ProjectVersion();
        packVersion.setId("pack-version-1");
        packVersion.setVersionNumber("2.0.0");
        packVersion.setDependencies(resolved.dependencies());
        Project pack = new Project();
        pack.setId("pack-1");
        pack.setSlug("test-pack");
        pack.setTitle("Test Pack");
        pack.setClassification(ProjectClassification.MODPACK);

        ProjectVersion hostedVersion = hostedProject.getVersions().getFirst();
        when(archiveSupport.resolveDependency(resolved.dependencies().getFirst()))
                .thenReturn(new DownloadArchiveSupport.ResolvedDependency(hostedProject, hostedVersion));
        when(archiveSupport.download("files/hosted.jar")).thenReturn("hosted-binary".getBytes(StandardCharsets.UTF_8));
        when(archiveSupport.extractOriginalFilename("files/hosted.jar")).thenReturn("hosted.jar");
        when(archiveSupport.newZipMultipartFile(eq("test-pack-2.0.0.zip"), any()))
                .thenAnswer(invocation -> mock(MultipartFile.class));
        when(archiveSupport.upload(any(MultipartFile.class), eq("modpacks"))).thenReturn("modpacks/test-pack.zip");

        byte[] archive = new ModpackArchiveService(projectRepository, archiveSupport).generateModpackZip(pack, packVersion);
        Map<String, byte[]> entries = unzip(archive);
        JsonNode manifest = new ObjectMapper().readTree(entries.get("modpack.json"));
        JsonNode lock = new ObjectMapper().readTree(entries.get("modtale.lock.json"));

        assertEquals("hosted-binary", new String(entries.get("hosted.jar"), StandardCharsets.UTF_8));
        assertFalse(entries.containsKey("SimpleCompost-1.0.0.jar"));
        JsonNode externalEntry = manifest.get("files").get(1);
        assertEquals("CURSEFORGE", externalEntry.get("source").asText());
        assertEquals("REFERENCE_ONLY", externalEntry.get("distribution").asText());
        assertEquals("https://www.curseforge.com/hytale/mods/simple-compost/files/8227810", externalEntry.get("url").asText());
        assertEquals("BUNDLED", lock.at("/entries/0/distribution").asText());
        assertEquals("hosted.jar", lock.at("/entries/0/path").asText());
        assertEquals(64, lock.at("/entries/0/hashes/sha256").asText().length());
        assertEquals("REFERENCE_ONLY", lock.at("/entries/1/distribution").asText());
        assertEquals("1450386", lock.at("/entries/1/provider/projectId").asText());
        assertEquals("8227810", lock.at("/entries/1/provider/fileId").asText());
        assertEquals(4096L, lock.at("/entries/1/provider/fileSize").asLong());
        assertEquals("a".repeat(40), lock.at("/entries/1/provider/hashes/sha1").asText());
        assertEquals("2026.09", lock.at("/entries/1/provider/gameVersions/0").asText());
        assertFalse(lock.at("/entries/1/provider/distributionAllowed").asBoolean());
    }

    private static DependencyReferenceRequest hostedReference() {
        DependencyReferenceRequest request = new DependencyReferenceRequest();
        request.setProjectId("hosted-mod");
        request.setVersionNumber("1.0.0");
        request.setSource(ProjectDependency.Source.MODTALE);
        return request;
    }

    private static DependencyReferenceRequest curseForgeReference() {
        DependencyReferenceRequest request = new DependencyReferenceRequest();
        request.setSource(ProjectDependency.Source.CURSEFORGE);
        request.setExternalId("1450386");
        request.setProjectTitle("Simple Compost");
        request.setVersionNumber("1.0.0");
        request.setExternalUrl("https://www.curseforge.com/hytale/mods/simple-compost/files/8227810");
        request.setExternalFileUrl("https://www.curseforge.com/hytale/mods/simple-compost/files/8227810");
        request.setExternalFileName("SimpleCompost-1.0.0.jar");
        request.setHytaleProjectConfirmed(true);
        return request;
    }

    private static Project hostedProject() {
        ProjectVersion version = new ProjectVersion();
        version.setVersionNumber("1.0.0");
        version.setFileUrl("files/hosted.jar");
        Project project = new Project();
        project.setId("hosted-mod");
        project.setTitle("Hosted Mod");
        project.setClassification(ProjectClassification.PLUGIN);
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setVersions(List.of(version));
        return project;
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                entries.put(entry.getName(), input.readAllBytes());
            }
        }
        return entries;
    }
}
