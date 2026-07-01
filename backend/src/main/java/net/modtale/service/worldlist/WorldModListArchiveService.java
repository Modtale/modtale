package net.modtale.service.worldlist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.exception.StorageDownloadException;
import net.modtale.model.worldlist.WorldModList;
import net.modtale.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

@Service
public class WorldModListArchiveService {

    private static final Logger logger = LoggerFactory.getLogger(WorldModListArchiveService.class);
    private final StorageService storageService;
    private final ObjectWriter manifestWriter;

    public WorldModListArchiveService(StorageService storageService, ObjectMapper mapper) {
        this.storageService = storageService;
        this.manifestWriter = mapper.writerWithDefaultPrettyPrinter();
    }

    public byte[] generateZip(WorldModList list) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            Set<String> entries = new HashSet<>();
            writeEntry(zip, entries, "modtale-list.json", manifestWriter.writeValueAsBytes(list));
            writeEntry(zip, entries, "README.txt", readme(list).getBytes(StandardCharsets.UTF_8));

            for (WorldModList.Item item : list.getMods()) {
                if (!item.isDownloadable() || item.getFileUrl() == null || item.getFileUrl().isBlank()) {
                    continue;
                }
                try {
                    byte[] file = storageService.download(item.getFileUrl());
                    writeEntry(zip, entries, filename(item), file);
                } catch (StorageDownloadException ex) {
                    logger.warn("Skipping unavailable world list file {} for list {}", item.getFileUrl(), list.getId(), ex);
                }
            }
        }
        return bytes.toByteArray();
    }

    private void writeEntry(ZipOutputStream zip, Set<String> entries, String rawName, byte[] data) throws IOException {
        String entryName = unique(entries, sanitize(rawName));
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(data == null ? new byte[0] : data);
        zip.closeEntry();
    }

    private String filename(WorldModList.Item item) {
        String base = firstText(item.getTitle(), item.getSlug(), item.getProjectId(), item.getModId(), "mod");
        String version = firstText(item.getVersionNumber(), "latest");
        return base + "-" + version + ".jar";
    }

    private String readme(WorldModList list) {
        return "Modtale world mod list\n"
                + "World: " + firstText(list.getWorldName(), "Unknown world") + "\n"
                + "List: " + firstText(list.getTitle(), "Shared mod list") + "\n"
                + "Game version: " + firstText(list.getGameVersion(), "Not specified") + "\n\n"
                + "This ZIP contains the downloadable Modtale projects from the shared list. "
                + "Some local or external entries may appear only in modtale-list.json.";
    }

    private static String unique(Set<String> entries, String filename) {
        String candidate = filename;
        int counter = 2;
        while (!entries.add(candidate)) {
            int dot = filename.lastIndexOf('.');
            candidate = dot > 0
                    ? filename.substring(0, dot) + "-" + counter + filename.substring(dot)
                    : filename + "-" + counter;
            counter++;
        }
        return candidate;
    }

    private static String sanitize(String filename) {
        String sanitized = firstText(filename, "modtale-list-file")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        if (sanitized.isBlank()) {
            return "modtale-list-file";
        }
        String lower = sanitized.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt") || lower.endsWith(".json") || lower.endsWith(".jar") || lower.endsWith(".zip")) {
            return sanitized;
        }
        return sanitized + ".jar";
    }

    private static String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
