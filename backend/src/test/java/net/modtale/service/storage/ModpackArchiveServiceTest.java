package net.modtale.service.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import net.modtale.exception.StorageDownloadException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.admin.review.ProjectReviewPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModpackArchiveServiceTest {

    private ProjectReviewPersistence reviewPersistence;
    private DownloadArchiveSupport archiveSupport;
    private ModpackArchiveService service;

    @BeforeEach
    void setUp() {
        reviewPersistence = mock(ProjectReviewPersistence.class);
        when(reviewPersistence.cacheModpackArchive(any(), any(), any(), any(), any())).thenReturn(true);
        archiveSupport = mock(DownloadArchiveSupport.class);
        service = new ModpackArchiveService(reviewPersistence, archiveSupport);
    }

    @Test
    void ownedConfigsGenerateValidatedV2ArchiveAndRoundTripSidecar() throws Exception {
        Project pack = pack();
        ProjectVersion version = version("1.0.0", null);
        ProjectDependency dependency = new ProjectDependency("plugin", "Plugin", "2.0.0");
        version.setDependencies(List.of(dependency));
        String path = "overrides/Universe/mods/Example_Plugin/config.json";
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes("{}")));
        var reference = new net.modtale.model.project.ModpackConfigReference("plugin", "MODTALE", path, hash);
        version.setModpackConfigs(List.of(reference));
        version.setOverrideFileUrl("configs.zip");
        String manifest = new ObjectMapper().writeValueAsString(Map.of("format", "modtale-configs", "formatVersion", 1, "configs", List.of(reference)));
        when(archiveSupport.download("configs.zip")).thenReturn(zip(Map.of(path, "{}", "modtale.configs.json", manifest)));
        when(archiveSupport.resolveDependency(dependency)).thenReturn(new DownloadArchiveSupport.ResolvedDependency(
                dependencyProject("plugin", ProjectClassification.PLUGIN), version("2.0.0", "plugin.jar")));
        when(archiveSupport.download("plugin.jar")).thenReturn(bytes("plugin-binary"));
        when(archiveSupport.extractOriginalFilename("plugin.jar")).thenReturn("plugin.jar");
        when(archiveSupport.newZipMultipartFile(any(), any())).thenReturn(mock(MultipartFile.class));
        byte[] archive = service.generateModpackZip(pack, version);
        ModpackArchiveValidator.validate(archive, true);
        Map<String, String> entries = unzip(archive);
        JsonNode lock = new ObjectMapper().readTree(entries.get("modtale.lock.json"));
        assertEquals(2, lock.path("lockVersion").asInt());
        assertEquals("plugin", lock.at("/overrides/0/owner/projectId").asText());
        assertEquals("Universe/mods/Example_Plugin/config.json", lock.at("/overrides/0/destination").asText());
        assertEquals("SEED_ONLY", lock.at("/overrides/0/installPolicy").asText());
        assertEquals(hash, new ObjectMapper().readTree(entries.get("modtale.configs.json")).at("/configs/0/sha256").asText());
        assertEquals("{}", entries.get(path));
        version.setFileUrl(null);
        assertArrayEquals(archive, service.generateModpackZip(pack, version));
    }

    @Test
    void generateModpackZipReturnsCachedArchiveWhenDownloadSucceeds() throws Exception {
        Project pack = pack();
        ProjectVersion version = version("1.0.0", "modpacks/cached.zip");

        byte[] cached = validEmptyArchive();
        when(archiveSupport.download("modpacks/cached.zip")).thenReturn(cached);

        assertArrayEquals(cached, service.generateModpackZip(pack, version));
        verify(reviewPersistence, never()).cacheModpackArchive(any(), any(), any(), any(), any());
    }

    @Test
    void generateModpackZipRebuildsLegacyArchivesThatPredateIntegrityLockfiles() throws Exception {
        Project pack = pack();
        ProjectVersion version = version("1.0.0", "modpacks/legacy.zip");

        when(archiveSupport.download("modpacks/legacy.zip")).thenReturn(zip(Map.of(
                "modpack.json", "{\"formatVersion\":1,\"game\":\"hytale\",\"files\":[]}"
        )));
        when(archiveSupport.newZipMultipartFile(eq("sky-pack-1.0.0.zip"), any()))
                .thenAnswer(invocation -> mock(MultipartFile.class));
        when(archiveSupport.upload(any(MultipartFile.class), eq("modpacks"))).thenReturn("modpacks/rebuilt.zip");

        Map<String, String> entries = unzip(service.generateModpackZip(pack, version));

        assertTrue(entries.containsKey("manifest.json"));
        assertTrue(entries.containsKey("modtale.lock.json"));
        assertEquals("modpacks/rebuilt.zip", version.getFileUrl());
        verify(reviewPersistence).cacheModpackArchive(eq(pack.getId()), any(), any(), any(), any());
    }

    @Test
    void generateModpackZipRebuildsAnEmptyCachedArchive() throws Exception {
        Project pack = pack();
        ProjectVersion version = version("1.0.0", "modpacks/empty.zip");

        when(archiveSupport.download("modpacks/empty.zip")).thenReturn(new byte[0]);
        when(archiveSupport.newZipMultipartFile(eq("sky-pack-1.0.0.zip"), any())).thenAnswer(invocation -> mock(MultipartFile.class));
        when(archiveSupport.upload(any(MultipartFile.class), eq("modpacks"))).thenReturn("modpacks/rebuilt.zip");

        Map<String, String> entries = unzip(service.generateModpackZip(pack, version));

        assertEquals(true, entries.containsKey("modpack.json"));
        assertEquals("modpacks/rebuilt.zip", version.getFileUrl());
        verify(reviewPersistence).cacheModpackArchive(eq(pack.getId()), any(), any(), any(), any());
    }

    @Test
    void generateModpackZipWritesValidJsonForTitlesWithControlCharacters() throws Exception {
        Project pack = pack();
        pack.setTitle("Sky \"Pack\"\nNight\tBuild");
        ProjectVersion version = version("1.0.0", null);
        ProjectDependency dependency = new ProjectDependency("plugin", "Plugin\nDeluxe", "2.0.0");
        version.setDependencies(List.of(dependency));
        Project plugin = dependencyProject("plugin", ProjectClassification.PLUGIN);
        ProjectVersion pluginVersion = version("2.0.0", "files/plugin.jar");

        when(archiveSupport.resolveDependency(dependency))
                .thenReturn(new DownloadArchiveSupport.ResolvedDependency(plugin, pluginVersion));
        when(archiveSupport.download("files/plugin.jar")).thenReturn(bytes("plugin-binary"));
        when(archiveSupport.extractOriginalFilename("files/plugin.jar")).thenReturn("plugin.jar");
        when(archiveSupport.newZipMultipartFile(eq("sky-pack-1.0.0.zip"), any())).thenAnswer(invocation -> mock(MultipartFile.class));
        when(archiveSupport.upload(any(MultipartFile.class), eq("modpacks"))).thenReturn("modpacks/generated.zip");

        String manifest = unzip(service.generateModpackZip(pack, version)).get("modpack.json");
        JsonNode parsed = new ObjectMapper().readTree(manifest);

        assertEquals("Sky \"Pack\"\nNight\tBuild", parsed.get("name").asText());
        assertEquals(1, parsed.get("formatVersion").asInt());
        assertEquals("hytale", parsed.get("game").asText());
        assertEquals("pack-1", parsed.get("packId").asText());
        assertEquals("1.0.0", parsed.get("versionNumber").asText());
        assertEquals("Plugin\nDeluxe", parsed.get("files").get(0).get("title").asText());
    }

    @Test
    void generateModpackZipWritesExactIntegrityLockfileAndSeparateIntentManifest() throws Exception {
        Project pack = pack();
        ProjectVersion version = version("1.0.0", null);
        version.setId("version-1");
        version.setGameVersions(List.of("2026.8", "2026.9"));
        ProjectDependency hosted = new ProjectDependency("plugin", "Plugin", "2.0.0");
        ProjectDependency curseForge = ProjectDependency.curseForge(
                "1450386",
                "External Mod",
                "1.0.0",
                "https://www.curseforge.com/hytale/mods/external-mod/files/8227810",
                ProjectDependency.DependencyType.OPTIONAL
        );
        curseForge.setExternalFileUrl("https://www.curseforge.com/hytale/mods/external-mod/files/8227810");
        version.setDependencies(List.of(hosted, curseForge));

        Project hostedProject = dependencyProject("plugin", ProjectClassification.PLUGIN);
        ProjectVersion hostedVersion = version("2.0.0", "files/plugin.jar");
        when(archiveSupport.resolveDependency(hosted))
                .thenReturn(new DownloadArchiveSupport.ResolvedDependency(hostedProject, hostedVersion));
        when(archiveSupport.download("files/plugin.jar")).thenReturn(bytes("plugin-binary"));
        when(archiveSupport.extractOriginalFilename("files/plugin.jar")).thenReturn("plugin.jar");
        when(archiveSupport.newZipMultipartFile(eq("sky-pack-1.0.0.zip"), any()))
                .thenAnswer(invocation -> mock(MultipartFile.class));
        when(archiveSupport.upload(any(MultipartFile.class), eq("modpacks"))).thenReturn("modpacks/generated.zip");

        Map<String, String> entries = unzip(service.generateModpackZip(pack, version));
        JsonNode manifest = new ObjectMapper().readTree(entries.get("manifest.json"));
        JsonNode lock = new ObjectMapper().readTree(entries.get("modtale.lock.json"));

        assertEquals("modtale-pack", manifest.get("format").asText());
        assertEquals("hytale", manifest.at("/game/id").asText());
        assertFalse(manifest.has("target"));
        assertEquals(List.of("2026.8", "2026.9"),
                new ObjectMapper().convertValue(manifest.at("/game/versions"), List.class));
        assertFalse(manifest.toString().contains("cachedFileUrl"));
        assertEquals("modtale-lock", lock.get("format").asText());
        assertEquals("hytale", lock.get("game").asText());
        assertFalse(lock.has("target"));
        assertEquals("BUNDLED", lock.at("/entries/0/distribution").asText());
        assertEquals("plugin.jar", lock.at("/entries/0/path").asText());
        assertEquals(bytes("plugin-binary").length, lock.at("/entries/0/size").asInt());
        assertEquals(64, lock.at("/entries/0/hashes/sha256").asText().length());
        assertEquals("REFERENCE_ONLY", lock.at("/entries/1/distribution").asText());
        assertEquals("1450386", lock.at("/entries/1/provider/projectId").asText());
        assertEquals("8227810", lock.at("/entries/1/provider/fileId").asText());
        assertEquals("REQUIRED", lock.at("/entries/1/dependencyType").asText());
        assertFalse(lock.at("/entries/1").has("environment"));
        assertFalse(entries.containsKey("External-Mod-1.0.0.jar"));
    }

    @Test
    void rejectsLegacySharedFilesInsteadOfShippingSaves() throws Exception {
        Project pack = pack();
        ProjectVersion version = version("1.0.0", null);
        version.setOverrideFileUrl("modpack-overrides/source.zip");
        when(archiveSupport.download("modpack-overrides/source.zip")).thenReturn(zip(Map.of(
                "overrides/Mods/example/game.json", "{}",
                "overrides/Saves/My World/mods/Example_Plugin/config.json", "{}"
        )));
        assertThrows(java.io.IOException.class, () -> service.generateModpackZip(pack, version));
    }

    @Test
    void generateModpackZipIsByteForByteDeterministicForTheSameInputs() throws Exception {
        Project pack = pack();
        ProjectVersion version = version("1.0.0", null);
        version.setId("version-1");
        ProjectDependency dependency = new ProjectDependency("plugin", "Plugin", "2.0.0");
        version.setDependencies(List.of(dependency));
        Project plugin = dependencyProject("plugin", ProjectClassification.PLUGIN);
        ProjectVersion pluginVersion = version("2.0.0", "files/plugin.jar");

        when(archiveSupport.resolveDependency(dependency))
                .thenReturn(new DownloadArchiveSupport.ResolvedDependency(plugin, pluginVersion));
        when(archiveSupport.download("files/plugin.jar")).thenReturn(bytes("plugin-binary"));
        when(archiveSupport.extractOriginalFilename("files/plugin.jar")).thenReturn("plugin.jar");
        when(archiveSupport.newZipMultipartFile(eq("sky-pack-1.0.0.zip"), any()))
                .thenAnswer(invocation -> mock(MultipartFile.class));
        when(archiveSupport.upload(any(MultipartFile.class), eq("modpacks"))).thenReturn("modpacks/generated.zip");

        byte[] first = service.generateModpackZip(pack, version);
        version.setFileUrl(null);
        byte[] second = service.generateModpackZip(pack, version);

        assertArrayEquals(first, second);
    }

    @Test
    void generateModpackZipPreventsCaseInsensitiveAndTraversalFilenameCollisions() throws Exception {
        Project pack = pack();
        ProjectVersion version = version("1.0.0", null);
        ProjectDependency first = new ProjectDependency("first", "First", "1.0.0");
        ProjectDependency second = new ProjectDependency("second", "Second", "1.0.0");
        version.setDependencies(List.of(first, second));
        Project firstProject = dependencyProject("first", ProjectClassification.PLUGIN);
        Project secondProject = dependencyProject("second", ProjectClassification.PLUGIN);
        ProjectVersion firstVersion = version("1.0.0", "files/first");
        ProjectVersion secondVersion = version("1.0.0", "files/second");

        when(archiveSupport.resolveDependency(first))
                .thenReturn(new DownloadArchiveSupport.ResolvedDependency(firstProject, firstVersion));
        when(archiveSupport.resolveDependency(second))
                .thenReturn(new DownloadArchiveSupport.ResolvedDependency(secondProject, secondVersion));
        when(archiveSupport.download("files/first")).thenReturn(bytes("first"));
        when(archiveSupport.download("files/second")).thenReturn(bytes("second"));
        when(archiveSupport.extractOriginalFilename("files/first")).thenReturn("../../Plugin.jar");
        when(archiveSupport.extractOriginalFilename("files/second")).thenReturn("plugin.JAR");
        when(archiveSupport.newZipMultipartFile(eq("sky-pack-1.0.0.zip"), any()))
                .thenAnswer(invocation -> mock(MultipartFile.class));

        Map<String, String> entries = unzip(service.generateModpackZip(pack, version));

        assertTrue(entries.containsKey("Plugin.jar"));
        assertTrue(entries.containsKey("plugin-2.JAR"));
        assertTrue(entries.keySet().stream().noneMatch(name -> name.contains("/") || name.contains("\\")));
    }

    @Test
    void generateModpackZipFailsInsteadOfSilentlyOmittingAnUnresolvedHostedDependency() {
        Project pack = pack();
        ProjectVersion version = version("1.0.0", null);
        ProjectDependency dependency = new ProjectDependency("missing", "Missing", "1.0.0");
        version.setDependencies(List.of(dependency));

        IOException error = assertThrows(IOException.class, () -> service.generateModpackZip(pack, version));

        assertTrue(error.getMessage().contains("Cannot resolve bundled Modtale dependency Missing"));
        verify(archiveSupport, never()).upload(any(MultipartFile.class), eq("modpacks"));
    }

    @Test
    void generateModpackZipRebuildsCachesAndWritesDependencyFilesAtArchiveRoot() throws Exception {
        Project pack = pack();
        ProjectVersion version = version("1.0.0", "modpacks/missing.zip");
        ProjectDependency externalDependency = ProjectDependency.curseForge(
                "1450386",
                "External Mod",
                "1.0.0",
                "https://www.curseforge.com/hytale/mods/external-mod/files/8227810",
                ProjectDependency.DependencyType.REQUIRED
        );
        externalDependency.setExternalFileName("External-Mod-1.0.0.jar");
        externalDependency.setCachedFileUrl("external-dependencies/curseforge/1450386/8227810/External-Mod-1.0.0.jar");
        version.setDependencies(List.of(
                new ProjectDependency("plugin", "Plugin", "2.0.0"),
                new ProjectDependency("data", "Data", "3.0.0"),
                externalDependency
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
        when(archiveSupport.download("external-dependencies/curseforge/1450386/8227810/External-Mod-1.0.0.jar")).thenReturn(bytes("external-binary"));
        when(archiveSupport.extractOriginalFilename("files/plugin.jar")).thenReturn("plugin.jar");
        when(archiveSupport.extractOriginalFilename("files/data.zip")).thenReturn("data.zip");
        when(archiveSupport.newZipMultipartFile(eq("sky-pack-1.0.0.zip"), any())).thenAnswer(invocation -> mock(MultipartFile.class));
        when(archiveSupport.upload(any(MultipartFile.class), eq("modpacks"))).thenReturn("modpacks/generated.zip");

        Map<String, String> entries = unzip(service.generateModpackZip(pack, version));

        assertEquals("modpacks/generated.zip", version.getFileUrl());
        assertEquals("plugin-binary", entries.get("plugin.jar"));
        assertEquals("data-binary", entries.get("data.zip"));
        assertFalse(entries.containsKey("External-Mod-1.0.0.jar"));
        assertTrue(entries.containsKey("manifest.json"));
        assertTrue(entries.containsKey("modtale.lock.json"));
        assertTrue(entries.get("modpack.json").contains("\"id\" : \"plugin\""));
        assertTrue(entries.get("modpack.json").contains("\"externalId\" : \"1450386\""));
        assertTrue(entries.get("modpack.json").contains("\"distribution\" : \"REFERENCE_ONLY\""));
        assertTrue(entries.get("modpack.json").contains("https://www.curseforge.com/hytale/mods/external-mod/files/8227810"));
        verify(reviewPersistence).cacheModpackArchive(eq(pack.getId()), any(), any(), any(), any());
    }

    @Test
    void cacheConflictDoesNotAttachOrReturnGeneratedArchive() throws Exception {
        Project pack = pack(); ProjectVersion version = version("1.0.0", null);
        when(archiveSupport.newZipMultipartFile(any(), any())).thenReturn(mock(MultipartFile.class));
        when(archiveSupport.upload(any(), eq("modpacks"))).thenReturn("modpacks/new.zip");
        when(reviewPersistence.cacheModpackArchive(any(), any(), any(), any(), any())).thenReturn(false);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.generateModpackZip(pack, version));
        org.junit.jupiter.api.Assertions.assertNull(version.getFileUrl());
    }

    @Test
    void rebuildingInvalidCacheUsesTokensFromBeforeTheLocalReferenceWasCleared() throws Exception {
        Project pack = pack(); ProjectVersion version = version("1.0.0", "modpacks/old.zip");
        version.setId("v1"); pack.setVersions(List.of(version));
        String projectToken = net.modtale.service.admin.review.ProjectReviewSnapshot.token(pack);
        String versionToken = net.modtale.service.admin.review.VersionReviewSnapshot.token(version);
        when(archiveSupport.download("modpacks/old.zip")).thenReturn(new byte[0]);
        when(archiveSupport.newZipMultipartFile(any(), any())).thenReturn(mock(MultipartFile.class));
        when(archiveSupport.upload(any(), eq("modpacks"))).thenReturn("modpacks/new.zip");
        service.generateModpackZip(pack, version);
        verify(reviewPersistence).cacheModpackArchive(pack.getId(), projectToken, "v1", versionToken, "modpacks/new.zip");
        assertEquals("modpacks/new.zip", version.getFileUrl());
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

    private static byte[] validEmptyArchive() throws IOException {
        return zip(Map.of(
                "modpack.json", "{\"formatVersion\":1,\"game\":\"hytale\",\"files\":[]}",
                "manifest.json", "{\"format\":\"modtale-pack\",\"schemaVersion\":1,\"pack\":{},\"game\":{\"id\":\"hytale\",\"versions\":[]},\"dependencies\":[]}",
                "modtale.lock.json", "{\"format\":\"modtale-lock\",\"lockVersion\":1,\"game\":\"hytale\",\"pack\":{},\"gameVersions\":[],\"entries\":[]}"
        ));
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> value : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(value.getKey());
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(value.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
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
