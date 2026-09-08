package net.modtale.service.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;

final class BundlePackagingService {

    private final DownloadArchiveSupport archiveSupport;

    BundlePackagingService(DownloadArchiveSupport archiveSupport) {
        this.archiveSupport = archiveSupport;
    }

    byte[] generateBundleZip(Project mainProject, ProjectVersion mainVersion, List<String> selectedDependencies) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeMainFile(zos, mainVersion);
            writeSelectedDependencies(zos, mainVersion, selectedDependencies);
        }
        return baos.toByteArray();
    }

    private void writeMainFile(ZipOutputStream zos, ProjectVersion mainVersion) throws IOException {
        if (mainVersion.getFileUrl() == null) {
            return;
        }

        byte[] mainData = archiveSupport.download(mainVersion.getFileUrl());
        String originalFilename = archiveSupport.extractOriginalFilename(mainVersion.getFileUrl());
        zos.putNextEntry(new ZipEntry(originalFilename));
        zos.write(mainData);
        zos.closeEntry();
    }

    private void writeSelectedDependencies(
            ZipOutputStream zos,
            ProjectVersion mainVersion,
            List<String> selectedDependencies
    ) throws IOException {
        if (mainVersion.getDependencies() == null) {
            return;
        }

        for (ProjectDependency dependency : mainVersion.getDependencies()) {
            if (dependency.isExternal()) {
                continue;
            }
            if (dependency.isEmbedded()) {
                continue;
            }
            if (selectedDependencies != null && !selectedDependencies.contains(dependency.getProjectId())) {
                continue;
            }

            DownloadArchiveSupport.ResolvedDependency resolvedDependency = archiveSupport.resolveDependency(dependency);
            if (resolvedDependency == null || resolvedDependency.version().getFileUrl() == null) {
                continue;
            }

            byte[] fileData = archiveSupport.download(resolvedDependency.version().getFileUrl());
            String originalFilename = archiveSupport.extractOriginalFilename(resolvedDependency.version().getFileUrl());
            zos.putNextEntry(new ZipEntry(originalFilename));
            zos.write(fileData);
            zos.closeEntry();
        }
    }
}
