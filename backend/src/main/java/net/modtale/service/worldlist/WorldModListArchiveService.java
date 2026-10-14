package net.modtale.service.worldlist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.exception.StorageDownloadException;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ProjectStatus;
import net.modtale.service.admin.review.VersionReviewSnapshot;
import net.modtale.model.worldlist.WorldModList;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.storage.DownloadService;
import net.modtale.service.storage.ApprovedArtifactBytes;
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
        List<ApprovalBinding> included = new ArrayList<>();
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
                if (approved == null) {
                    continue;
                }
                try {
                    byte[] file = approved.project().getClassification() == ProjectClassification.MODPACK
                            ? downloadService.generateModpackZip(approved.project(), approved.version(), null, approved.cacheBinding(),
                                    approved.bindings().stream().skip(1).map(ApprovalBinding::snapshot).toList())
                            : ApprovedArtifactBytes.requireExact(approved.bindings().getFirst().byteApproval(),
                                    storageService.downloadBounded(approved.bindings().getFirst().fileReference(), StorageService.MAX_REVIEW_ARTIFACT_BYTES));
                    writeEntry(zip, entries, filename(item), file);
                    included.addAll(approved.bindings());
                } catch (StorageDownloadException | IOException ex) {
                    logger.warn("Skipping unavailable world list file {} for list {}", item.getFileUrl(), list.getId(), ex);
                }
            }
        }
        // Validate every included item after all storage/generation I/O, outside the per-item skip handler.
        for (ApprovalBinding binding : included) requireCurrent(binding);
        return bytes.toByteArray();
    }

    private ApprovedArtifact currentlyApproved(WorldModList.Item item) {
        if (!item.isDownloadable() || item.getFileUrl() == null || item.getFileUrl().isBlank()
                || item.getSource() != ProjectDependency.Source.MODTALE || item.getProjectId() == null
                || item.getProjectId().isBlank() || item.getVersionNumber() == null) return null;
        Project project = projectService.getRawProjectById(item.getProjectId());
        if (!publiclyAvailable(project) || !item.getProjectId().equals(project.getId())) return null;
        ProjectVersion version = uniqueVersion(project, item.getVersionNumber(), false);
        if (version == null || !item.getFileUrl().equals(version.getFileUrl())) return null;
        boolean modpack = project.getClassification() == ProjectClassification.MODPACK;
        ApprovalBinding parent = bind(project, version, modpack);
        if (parent == null) return null;
        String cacheBinding = modpack ? DownloadService.modpackCacheBinding(project, version) : null;
        List<ApprovalBinding> bindings = new ArrayList<>();
        bindings.add(parent);
        if (modpack && version.getDependencies() != null) {
            for (ProjectDependency dependency : version.getDependencies()) {
                if (dependency == null) return null;
                if (dependency.isExternal()) continue;
                Project source = projectService.getRawProjectById(dependency.getProjectId());
                if (!publiclyAvailable(source) || !Objects.equals(dependency.getProjectId(), source.getId())) return null;
                ProjectVersion child = uniqueVersion(source, dependency.getVersionNumber(), false);
                ApprovalBinding binding = bind(source, child, false);
                if (binding == null) return null;
                bindings.add(binding);
            }
        }
        return new ApprovedArtifact(project, version, List.copyOf(bindings), cacheBinding);
    }

    private boolean publiclyAvailable(Project project) {
        return project != null && project.getDeletedAt() == null && project.getStatus() != ProjectStatus.DELETED
                && accessControlService.isPubliclyReadable(project);
    }

    private ProjectVersion uniqueVersion(Project project, String selector, boolean byId) {
        if (selector == null || selector.isBlank() || project.getVersions() == null) return null;
        var matches = project.getVersions().stream().filter(version -> version != null
                && selector.equals(byId ? version.getId() : version.getVersionNumber())).limit(2).toList();
        if (matches.size() != 1) return null;
        ProjectVersion version = matches.getFirst();
        return version.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED ? version : null;
    }

    private ApprovalBinding bind(Project project, ProjectVersion version, boolean generatedArchive) {
        if (version == null || version.getId() == null || version.getId().isBlank()
                || project.getId() == null || project.getId().isBlank()
                || version.getFileUrl() == null || version.getFileUrl().isBlank()) return null;
        return new ApprovalBinding(project, version, project.getId(), version.getId(), project.getClassification(),
                version.getFileUrl(), snapshot(version, generatedArchive), generatedArchive, byteApproval(version));
    }

    private void requireCurrent(ApprovalBinding binding) throws IOException {
        if (!publiclyAvailable(binding.preparedProject())
                || !binding.projectId().equals(binding.preparedProject().getId())
                || binding.classification() != binding.preparedProject().getClassification()
                || !binding.snapshot().equals(snapshot(binding.preparedVersion(), binding.generatedArchive()))) {
            throw new IOException("A reviewed world list artifact changed while the archive was being prepared.");
        }
        Project current = projectService.getRawProjectById(binding.projectId());
        if (!publiclyAvailable(current) || !binding.projectId().equals(current.getId())
                || binding.classification() != current.getClassification()) {
            throw new IOException("A world list project is no longer available for archive delivery.");
        }
        ProjectVersion version = uniqueVersion(current, binding.versionId(), true);
        if (version == null || !binding.snapshot().equals(snapshot(version, binding.generatedArchive()))) {
            throw new IOException("A reviewed world list artifact is no longer current for archive delivery.");
        }
    }

    private static ProjectVersion byteApproval(ProjectVersion source) {
        // Storage I/O must not be able to change the hash against which its own result is checked.
        ProjectVersion frozen = new ProjectVersion();
        frozen.setReviewStatus(source.getReviewStatus());
        frozen.setHash(source.getHash());
        frozen.setApprovedSecurityEvidence(source.getApprovedSecurityEvidence());
        return frozen;
    }

    private static String snapshot(ProjectVersion version, boolean generatedArchive) {
        return generatedArchive ? VersionReviewSnapshot.modpackArchiveToken(version) : VersionReviewSnapshot.token(version);
    }

    private record ApprovedArtifact(Project project, ProjectVersion version, List<ApprovalBinding> bindings, String cacheBinding) {}

    private record ApprovalBinding(Project preparedProject, ProjectVersion preparedVersion, String projectId,
            String versionId, ProjectClassification classification, String fileReference, String snapshot,
            boolean generatedArchive, ProjectVersion byteApproval) {}

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
