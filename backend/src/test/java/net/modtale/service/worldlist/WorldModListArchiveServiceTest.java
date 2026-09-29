package net.modtale.service.worldlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.worldlist.WorldModList;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.storage.StorageService;
import net.modtale.service.storage.DownloadService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WorldModListArchiveServiceTest {

    @Test
    void generateZipIncludesManifestReadmeAndDownloadableFilesOnly() throws IOException {
        StorageService storageService = mock(StorageService.class);
        ProjectService projectService = mock(ProjectService.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        when(storageService.downloadBounded("storage/cool.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES))
                .thenReturn("cool-bytes".getBytes(StandardCharsets.UTF_8));
        Project project = new Project();
        project.setId("project-1");
        ProjectVersion approved = new ProjectVersion();
        approved.setVersionNumber("1.0.0");
        approved.setFileUrl("storage/cool.jar");
        approved.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        approved.setHash(sha256("cool-bytes"));
        project.setVersions(List.of(approved));
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.isPubliclyReadable(project)).thenReturn(true);

        WorldModList list = new WorldModList();
        list.setId("list-1");
        list.setTitle("Shared list");
        list.setWorldName("Cozy World");
        list.setGameVersion("0.5.0");
        list.setCreatedAt(Instant.parse("2026-06-20T12:00:00Z"));
        list.setLastViewedAt(Instant.parse("2026-06-20T12:30:00Z"));
        list.setExpiresAt(Instant.parse("2026-07-20T12:00:00Z"));
        list.setConfigs(List.of(new net.modtale.model.worldlist.WorldListConfig("WORLD", "Example_Plugin/config.json", "{\"value\":2}")));
        WorldModList.Item approvedItem = item("Cool Mod", "1.0.0", true, "storage/cool.jar");
        approvedItem.setProjectId("project-1");
        list.setMods(List.of(approvedItem, item("External Mod", "0.2.0", false, "")));

        byte[] archive = new WorldModListArchiveService(storageService, new ObjectMapper(), projectService, accessControlService, mock(DownloadService.class)).generateZip(list);
        Map<String, String> entries = entries(archive);

        assertTrue(entries.containsKey("modtale-list.json"));
        assertEquals("{\"value\":2}", entries.get("configs/world/mods/Example_Plugin/config.json"));
        assertTrue(entries.get("modtale-list.json").contains("Example_Plugin/config.json"));
        assertTrue(entries.get("modtale-list.json").contains("\"createdAt\" : \"2026-06-20T12:00:00Z\""));
        assertTrue(entries.get("README.txt").contains("Cozy World"));
        assertEquals("cool-bytes", entries.get("Cool-Mod-1.0.0.jar"));
        assertFalse(entries.containsKey("External-Mod-0.2.0.jar"));
    }

    @Test
    void staleDownloadableFlagCannotPackageAWithdrawnArtifact() throws IOException {
        StorageService storageService = mock(StorageService.class);
        ProjectService projectService = mock(ProjectService.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        WorldModList.Item stale = item("Withdrawn Mod", "2.0.0", true, "storage/withdrawn.jar");
        stale.setProjectId("project-1");
        WorldModList list = new WorldModList();
        list.setId("list-1");
        list.setMods(List.of(stale));
        Project project = new Project();
        project.setId("project-1");
        ProjectVersion version = new ProjectVersion();
        version.setVersionNumber("2.0.0");
        version.setFileUrl("storage/withdrawn.jar");
        version.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        project.setVersions(List.of(version));
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.isPubliclyReadable(project)).thenReturn(true);
        when(storageService.download("storage/withdrawn.jar")).thenReturn("old-bytes".getBytes(StandardCharsets.UTF_8));

        Map<String, String> entries = entries(new WorldModListArchiveService(storageService, new ObjectMapper(), projectService, accessControlService, mock(DownloadService.class)).generateZip(list));

        assertFalse(entries.containsKey("Withdrawn-Mod-2.0.0.jar"));
    }

    @Test
    void changedStorageBytesAreOmittedFromSharedListZip() throws IOException {
        StorageService storageService = mock(StorageService.class);
        ProjectService projectService = mock(ProjectService.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        WorldModList.Item item = item("Reviewed Mod", "1.0.0", true, "storage/mod.jar");
        item.setProjectId("project-1");
        WorldModList list = new WorldModList();
        list.setId("list-1");
        list.setMods(List.of(item));
        Project project = new Project();
        project.setId("project-1");
        ProjectVersion version = new ProjectVersion();
        version.setVersionNumber("1.0.0");
        version.setFileUrl("storage/mod.jar");
        version.setHash(sha256("reviewed"));
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        project.setVersions(List.of(version));
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.isPubliclyReadable(project)).thenReturn(true);
        when(storageService.downloadBounded("storage/mod.jar", StorageService.MAX_REVIEW_ARTIFACT_BYTES))
                .thenReturn("replaced".getBytes(StandardCharsets.UTF_8));

        Map<String, String> entries = entries(new WorldModListArchiveService(storageService, new ObjectMapper(),
                projectService, accessControlService, mock(DownloadService.class)).generateZip(list));

        assertFalse(entries.containsKey("Reviewed-Mod-1.0.0.jar"));
    }

    @Test
    void cachedModpackCannotPackageAWithdrawnDependency() throws IOException {
        StorageService storageService = mock(StorageService.class);
        ProjectService projectService = mock(ProjectService.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        WorldModList.Item item = item("Modpack", "1.0.0", true, "storage/pack.zip");
        item.setProjectId("pack");
        WorldModList list = new WorldModList();
        list.setId("list-1");
        list.setMods(List.of(item));
        Project pack = new Project();
        pack.setId("pack");
        pack.setClassification(ProjectClassification.MODPACK);
        ProjectVersion packVersion = new ProjectVersion();
        packVersion.setVersionNumber("1.0.0");
        packVersion.setFileUrl("storage/pack.zip");
        packVersion.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        packVersion.setDependencies(List.of(new ProjectDependency("plugin", "Plugin", "2.0.0")));
        pack.setVersions(List.of(packVersion));
        Project plugin = new Project();
        plugin.setId("plugin");
        ProjectVersion withdrawn = new ProjectVersion();
        withdrawn.setVersionNumber("2.0.0");
        withdrawn.setFileUrl("storage/plugin.jar");
        withdrawn.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        plugin.setVersions(List.of(withdrawn));
        when(projectService.getRawProjectById("pack")).thenReturn(pack);
        when(projectService.getRawProjectById("plugin")).thenReturn(plugin);
        when(accessControlService.isPubliclyReadable(pack)).thenReturn(true);
        when(accessControlService.isPubliclyReadable(plugin)).thenReturn(true);
        when(storageService.download("storage/pack.zip")).thenReturn("stale-pack".getBytes(StandardCharsets.UTF_8));

        Map<String, String> entries = entries(new WorldModListArchiveService(storageService, new ObjectMapper(),
                projectService, accessControlService, mock(DownloadService.class)).generateZip(list));

        assertFalse(entries.containsKey("Modpack-1.0.0.jar"));
    }

    @Test
    void approvedModpackUsesCurrentArchiveInsteadOfCachedFileBytes() throws IOException {
        StorageService storageService = mock(StorageService.class);
        ProjectService projectService = mock(ProjectService.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        DownloadService downloadService = mock(DownloadService.class);
        WorldModList.Item item = item("Modpack", "1.0.0", true, "storage/pack.zip");
        item.setProjectId("pack");
        WorldModList list = new WorldModList();
        list.setId("list-1");
        list.setMods(List.of(item));
        Project pack = new Project();
        pack.setId("pack");
        pack.setClassification(ProjectClassification.MODPACK);
        ProjectVersion version = new ProjectVersion();
        version.setVersionNumber("1.0.0");
        version.setFileUrl("storage/pack.zip");
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        pack.setVersions(List.of(version));
        when(projectService.getRawProjectById("pack")).thenReturn(pack);
        when(accessControlService.isPubliclyReadable(pack)).thenReturn(true);
        when(storageService.download("storage/pack.zip")).thenReturn("stale-bytes".getBytes(StandardCharsets.UTF_8));
        when(downloadService.generateModpackZip(pack, version, null)).thenReturn("current-bytes".getBytes(StandardCharsets.UTF_8));

        Map<String, String> entries = entries(new WorldModListArchiveService(storageService, new ObjectMapper(),
                projectService, accessControlService, downloadService).generateZip(list));

        assertEquals("current-bytes", entries.get("Modpack-1.0.0.jar"));
    }

    @Test
    void cachedFilePathCannotPackageDifferentApprovedBytes() throws IOException {
        StorageService storageService = mock(StorageService.class);
        ProjectService projectService = mock(ProjectService.class);
        AccessControlService accessControlService = mock(AccessControlService.class);
        WorldModList.Item stale = item("Changed Mod", "2.0.0", true, "storage/old.jar");
        stale.setProjectId("project-1");
        WorldModList list = new WorldModList();
        list.setId("list-1");
        list.setMods(List.of(stale));
        Project project = new Project();
        project.setId("project-1");
        ProjectVersion current = new ProjectVersion();
        current.setVersionNumber("2.0.0");
        current.setFileUrl("storage/new.jar");
        current.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        project.setVersions(List.of(current));
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.isPubliclyReadable(project)).thenReturn(true);
        when(storageService.download("storage/old.jar")).thenReturn("old-bytes".getBytes(StandardCharsets.UTF_8));

        Map<String, String> entries = entries(new WorldModListArchiveService(storageService, new ObjectMapper(), projectService, accessControlService, mock(DownloadService.class)).generateZip(list));

        assertFalse(entries.containsKey("Changed-Mod-2.0.0.jar"));
    }

    private static WorldModList.Item item(String title, String version, boolean downloadable, String fileUrl) {
        WorldModList.Item item = new WorldModList.Item();
        item.setId(title);
        item.setTitle(title);
        item.setVersionNumber(version);
        item.setClassification(ProjectClassification.PLUGIN);
        item.setSource(ProjectDependency.Source.MODTALE);
        item.setDownloadable(downloadable);
        item.setFileUrl(fileUrl);
        return item;
    }

    private static Map<String, String> entries(byte[] archive) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
