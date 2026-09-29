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
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.worldlist.WorldModList;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.storage.DownloadService;
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
    private final ProjectService projectService;
    private final AccessControlService accessControlService;
    private final DownloadService downloadService;
    private final ObjectWriter manifestWriter;

    public WorldModListArchiveService(StorageService storageService, ObjectMapper mapper,
            ProjectService projectService, AccessControlService accessControlService, DownloadService downloadService) {
        this.storageService = storageService;
        this.projectService = projectService;
        this.accessControlService = accessControlService;
        this.downloadService = downloadService;
        this.manifestWriter = mapper.writerWithDefaultPrettyPrinter();
    }

    public byte[] generateZip(WorldModList list) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            Set<String> entries = new HashSet<>();
            writeEntry(zip, entries, "modtale-list.json", manifestWriter.writeValueAsBytes(list));
            writeEntry(zip, entries, "README.txt", readme(list).getBytes(StandardCharsets.UTF_8));

            for (var config : net.modtale.model.worldlist.WorldListConfig.validate(list.getConfigs())) {
                zip.putNextEntry(new ZipEntry(config.archivePath()));
                zip.write(config.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            for (WorldModList.Item item : list.getMods()) {
                ApprovedArtifact approved = currentlyApproved(item);
                if (!item.isDownloadable() || item.getFileUrl() == null || item.getFileUrl().isBlank()
                        || approved == null) {
                    continue;
                }
                try {
                    byte[] file = approved.project().getClassification() == ProjectClassification.MODPACK
                            ? downloadService.generateModpackZip(approved.project(), approved.version(), null)
                            : storageService.download(item.getFileUrl());
                    writeEntry(zip, entries, filename(item), file);
                } catch (StorageDownloadException | IOException ex) {
                    logger.warn("Skipping unavailable world list file {} for list {}", item.getFileUrl(), list.getId(), ex);
                }
            }
        }
        return bytes.toByteArray();
    }

    private ApprovedArtifact currentlyApproved(WorldModList.Item item) {
        if (item.getSource() != ProjectDependency.Source.MODTALE || item.getProjectId() == null
                || item.getProjectId().isBlank() || item.getVersionNumber() == null) return null;
        var project = projectService.getRawProjectById(item.getProjectId());
        if (project == null || !accessControlService.isPubliclyReadable(project) || project.getVersions() == null) return null;
        var matches = project.getVersions().stream().filter(version -> version != null
                && version.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED
                && item.getVersionNumber().equals(version.getVersionNumber())
                && item.getFileUrl().equals(version.getFileUrl())
                && (project.getClassification() != ProjectClassification.MODPACK || bundledDependenciesAvailable(version)))
                .limit(2).toList();
        return matches.size() == 1 ? new ApprovedArtifact(project, matches.getFirst()) : null;
    }

    private record ApprovedArtifact(Project project, ProjectVersion version) {}

    private boolean bundledDependenciesAvailable(ProjectVersion version) {
        if (version.getDependencies() == null) return true;
        for (ProjectDependency dependency : version.getDependencies()) {
            if (dependency.isExternal()) continue;
            var project = projectService.getRawProjectById(dependency.getProjectId());
            if (project == null || !accessControlService.isPubliclyReadable(project) || project.getVersions() == null) return false;
            if (project.getVersions().stream().filter(candidate -> candidate != null
                    && candidate.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED
                    && dependency.getVersionNumber().equals(candidate.getVersionNumber())
                    && candidate.getFileUrl() != null && !candidate.getFileUrl().isBlank()).limit(2).count() != 1) return false;
        }
        return true;
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
                + "Some local or external entries may appear only in modtale-list.json. "
                + "Selected config defaults are in configs/global/Mods/ and configs/world/mods/. "
                + "Copy global configs into UserData/Mods and world configs into the target save's mods folder. "
                + "Preserve existing files and close Hytale before applying configs.";
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
