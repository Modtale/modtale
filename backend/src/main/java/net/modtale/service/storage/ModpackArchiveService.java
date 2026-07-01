package net.modtale.service.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.exception.StorageDownloadException;
import net.modtale.exception.StorageUploadException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.repository.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ModpackArchiveService {

    private static final Logger logger = LoggerFactory.getLogger(ModpackArchiveService.class);

    private final ProjectRepository projectRepository;
    private final DownloadArchiveSupport archiveSupport;

    ModpackArchiveService(ProjectRepository projectRepository, DownloadArchiveSupport archiveSupport) {
        this.projectRepository = projectRepository;
        this.archiveSupport = archiveSupport;
    }

    byte[] generateModpackZip(Project pack, ProjectVersion version) throws IOException {
        byte[] cachedArchive = downloadCachedArchive(pack, version);
        if (cachedArchive != null) {
            return cachedArchive;
        }

        byte[] zipBytes = buildArchive(pack, version);
        cacheArchive(pack, version, zipBytes);
        return zipBytes;
    }

    private byte[] downloadCachedArchive(Project pack, ProjectVersion version) {
        if (version.getFileUrl() == null) {
            return null;
        }

        try {
            return archiveSupport.download(version.getFileUrl());
        } catch (StorageDownloadException ex) {
            logger.warn("Cached modpack archive could not be downloaded for project={} version={}. Rebuilding archive.",
                    pack.getId(), version.getVersionNumber(), ex);
            version.setFileUrl(null);
            return null;
        }
    }

    private byte[] buildArchive(Project pack, ProjectVersion version) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Set<String> archiveEntries = new HashSet<>();
            writeManifest(zos, pack, version);
            archiveEntries.add("modpack.json");
            writeDependencyFiles(zos, version, archiveEntries);
        }
        return baos.toByteArray();
    }

    private void writeManifest(ZipOutputStream zos, Project pack, ProjectVersion version) throws IOException {
        ZipEntry readme = new ZipEntry("modpack.json");
        zos.putNextEntry(readme);

        StringBuilder json = new StringBuilder("{\n  \"name\": \"")
                .append(jsonEscape(pack.getTitle()))
                .append("\",\n  \"files\": [\n");
        if (version.getDependencies() != null) {
            for (int i = 0; i < version.getDependencies().size(); i++) {
                ProjectDependency dep = version.getDependencies().get(i);
                json.append("    { \"id\": \"").append(jsonEscape(dep.getProjectId()))
                        .append("\", \"title\": \"").append(jsonEscape(dep.getProjectTitle()))
                        .append("\", \"version\": \"").append(jsonEscape(dep.getVersionNumber()))
                        .append("\", \"source\": \"").append(jsonEscape(dep.getSource().name())).append("\"");
                if (dep.isExternal()) {
                    json.append(", \"externalId\": \"").append(jsonEscape(dep.getExternalId()))
                            .append("\", \"url\": \"").append(jsonEscape(dep.getExternalUrl())).append("\"");
                    if (dep.getExternalFileUrl() != null) {
                        json.append(", \"externalFileUrl\": \"").append(jsonEscape(dep.getExternalFileUrl())).append("\"");
                    }
                    if (dep.getExternalFileName() != null) {
                        json.append(", \"externalFileName\": \"").append(jsonEscape(dep.getExternalFileName())).append("\"");
                    }
                    if (dep.getCachedFileUrl() != null) {
                        json.append(", \"cachedFileUrl\": \"").append(jsonEscape(dep.getCachedFileUrl())).append("\"");
                    }
                }
                json.append(" }");
                if (i < version.getDependencies().size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
        }
        json.append("  ]\n}");

        zos.write(json.toString().getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private void writeDependencyFiles(ZipOutputStream zos, ProjectVersion version, Set<String> archiveEntries) throws IOException {
        if (version.getDependencies() == null) {
            return;
        }

        for (ProjectDependency dependency : version.getDependencies()) {
            if (dependency.isExternal()) {
                writeExternalDependencyFile(zos, dependency, archiveEntries);
                continue;
            }

            DownloadArchiveSupport.ResolvedDependency resolvedDependency = archiveSupport.resolveDependency(dependency);
            if (resolvedDependency == null || resolvedDependency.version().getFileUrl() == null) {
                continue;
            }

            byte[] fileData = archiveSupport.download(resolvedDependency.version().getFileUrl());
            String originalFilename = archiveSupport.extractOriginalFilename(resolvedDependency.version().getFileUrl());

            writeArchiveEntry(zos, archiveEntries, originalFilename, fileData);
        }
    }

    private void writeExternalDependencyFile(ZipOutputStream zos, ProjectDependency dependency, Set<String> archiveEntries) throws IOException {
        String cachedFileUrl = trimToNull(dependency.getCachedFileUrl());
        if (cachedFileUrl == null) {
            return;
        }

        try {
            byte[] fileData = archiveSupport.download(cachedFileUrl);
            if (fileData == null || fileData.length == 0) {
                return;
            }

            writeArchiveEntry(zos, archiveEntries, externalFilename(dependency), fileData);
        } catch (StorageDownloadException ex) {
            logger.warn("Unable to include cached external dependency {} from {} in generated modpack archive.",
                    dependency.getProjectTitle(), cachedFileUrl, ex);
        }
    }

    private void writeArchiveEntry(ZipOutputStream zos, Set<String> archiveEntries, String filename, byte[] fileData) throws IOException {
        String entryName = uniqueArchiveEntryName(archiveEntries, sanitizeArchiveFilename(filename));
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(fileData);
        zos.closeEntry();
    }

    private String uniqueArchiveEntryName(Set<String> archiveEntries, String filename) {
        String candidate = filename;
        int counter = 2;
        while (!archiveEntries.add(candidate)) {
            int extensionStart = filename.lastIndexOf('.');
            if (extensionStart > 0) {
                candidate = filename.substring(0, extensionStart) + "-" + counter + filename.substring(extensionStart);
            } else {
                candidate = filename + "-" + counter;
            }
            counter++;
        }
        return candidate;
    }

    private String externalFilename(ProjectDependency dependency) {
        String filename = trimToNull(dependency.getExternalFileName());
        if (filename == null && trimToNull(dependency.getCachedFileUrl()) != null) {
            filename = archiveSupport.extractOriginalFilename(dependency.getCachedFileUrl());
        }
        if (filename != null) {
            return sanitizeArchiveFilename(filename);
        }

        String title = dependency.getProjectTitle() == null ? dependency.getProjectId() : dependency.getProjectTitle();
        String version = dependency.getVersionNumber() == null ? "latest" : dependency.getVersionNumber();
        String base = (title + "-" + version)
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return (base.isBlank() ? "external-dependency" : base) + ".jar";
    }

    private String sanitizeArchiveFilename(String filename) {
        String sanitized = filename.replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        if (sanitized.isBlank()) {
            return "external-dependency.jar";
        }
        String lower = sanitized.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".zip") ? sanitized : sanitized + ".jar";
    }

    private void cacheArchive(Project pack, ProjectVersion version, byte[] zipBytes) {
        try {
            String fileName = (pack.getSlug() != null && !pack.getSlug().isEmpty() ? pack.getSlug() : pack.getId())
                    + "-"
                    + version.getVersionNumber()
                    + ".zip";
            String uploadPath = archiveSupport.upload(
                    archiveSupport.newZipMultipartFile(fileName, zipBytes),
                    "modpacks"
            );

            version.setFileUrl(uploadPath);
            projectRepository.save(pack);
        } catch (StorageUploadException ex) {
            logger.warn("Generated modpack archive could not be cached for project={} version={}",
                    pack.getId(), version.getVersionNumber(), ex);
        }
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
