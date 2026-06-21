package net.modtale.service.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
            writeManifest(zos, pack, version);
            writeDependencyFiles(zos, version);
        }
        return baos.toByteArray();
    }

    private void writeManifest(ZipOutputStream zos, Project pack, ProjectVersion version) throws IOException {
        ZipEntry readme = new ZipEntry("modpack.json");
        zos.putNextEntry(readme);

        StringBuilder json = new StringBuilder("{\n  \"name\": \"" + pack.getTitle() + "\",\n  \"files\": [\n");
        if (version.getDependencies() != null) {
            for (int i = 0; i < version.getDependencies().size(); i++) {
                ProjectDependency dep = version.getDependencies().get(i);
                json.append("    { \"id\": \"").append(dep.getModId())
                        .append("\", \"version\": \"").append(dep.getVersionNumber()).append("\" }");
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

    private void writeDependencyFiles(ZipOutputStream zos, ProjectVersion version) throws IOException {
        if (version.getDependencies() == null) {
            return;
        }

        for (ProjectDependency dependency : version.getDependencies()) {
            DownloadArchiveSupport.ResolvedDependency resolvedDependency = archiveSupport.resolveDependency(dependency);
            if (resolvedDependency == null || resolvedDependency.version().getFileUrl() == null) {
                continue;
            }

            byte[] fileData = archiveSupport.download(resolvedDependency.version().getFileUrl());
            String folder = resolvedDependency.project().getClassification() != null
                    && "PLUGIN".equals(resolvedDependency.project().getClassification().name())
                    ? "plugins/"
                    : "asset-packs/";
            String originalFilename = archiveSupport.extractOriginalFilename(resolvedDependency.version().getFileUrl());

            zos.putNextEntry(new ZipEntry(folder + originalFilename));
            zos.write(fileData);
            zos.closeEntry();
        }
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
}
