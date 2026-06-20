package net.modtale.service.worldlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.worldlist.WorldModList;
import net.modtale.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WorldModListArchiveServiceTest {

    @Test
    void generateZipIncludesManifestReadmeAndDownloadableFilesOnly() throws IOException {
        StorageService storageService = mock(StorageService.class);
        when(storageService.download("storage/cool.jar")).thenReturn("cool-bytes".getBytes(StandardCharsets.UTF_8));

        WorldModList list = new WorldModList();
        list.setId("list-1");
        list.setTitle("Shared list");
        list.setWorldName("Cozy World");
        list.setGameVersion("0.5.0");
        list.setCreatedAt(Instant.parse("2026-06-20T12:00:00Z"));
        list.setLastViewedAt(Instant.parse("2026-06-20T12:30:00Z"));
        list.setExpiresAt(Instant.parse("2026-07-20T12:00:00Z"));
        list.setMods(List.of(
                item("Cool Mod", "1.0.0", true, "storage/cool.jar"),
                item("External Mod", "0.2.0", false, "")
        ));

        byte[] archive = new WorldModListArchiveService(storageService, new ObjectMapper()).generateZip(list);
        Map<String, String> entries = entries(archive);

        assertTrue(entries.containsKey("modtale-list.json"));
        assertTrue(entries.get("modtale-list.json").contains("\"createdAt\" : \"2026-06-20T12:00:00Z\""));
        assertTrue(entries.get("README.txt").contains("Cozy World"));
        assertEquals("cool-bytes", entries.get("Cool-Mod-1.0.0.jar"));
        assertFalse(entries.containsKey("External-Mod-0.2.0.jar"));
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
}
