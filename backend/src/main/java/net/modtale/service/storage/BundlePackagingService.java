package net.modtale.service.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        Set<String> selected = validatedSelection(mainVersion, selectedDependencies);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeMainFile(zos, mainVersion);
            writeSelectedDependencies(zos, mainVersion, selected);
        }
        return baos.toByteArray();
    }

    private Set<String> validatedSelection(ProjectVersion version, List<String> requested) throws IOException {
        if (requested == null) return null;
        Set<String> available = new HashSet<>();
        if (version.getDependencies() != null) {
            for (ProjectDependency dependency : version.getDependencies()) {
                if (dependency != null && !dependency.isExternal() && !dependency.isEmbedded()
                        && dependency.getProjectId() != null) available.add(dependency.getProjectId());
            }
        }
        Set<String> selected = new HashSet<>();
        for (String projectId : requested) {
            if (projectId == null || !available.contains(projectId) || !selected.add(projectId)) {
                throw new IOException("The selected bundle dependencies no longer match this version.");
            }
        }
        return selected;
    }

    private void writeMainFile(ZipOutputStream zos, ProjectVersion mainVersion) throws IOException {
        if (mainVersion.getFileUrl() == null || mainVersion.getFileUrl().isBlank()) {
            throw new IOException("The main version has no approved artifact to package.");
        }

        byte[] mainData = archiveSupport.downloadApproved(mainVersion);
        String originalFilename = archiveSupport.extractOriginalFilename(mainVersion.getFileUrl());
        zos.putNextEntry(new ZipEntry(originalFilename));
        zos.write(mainData);
        zos.closeEntry();
    }

    private void writeSelectedDependencies(
            ZipOutputStream zos,
            ProjectVersion mainVersion,
            Set<String> selectedDependencies
    ) throws IOException {
        if (mainVersion.getDependencies() == null) {
            return;
        }

        for (ProjectDependency dependency : mainVersion.getDependencies()) {
            if (dependency == null) throw new IOException("A bundle dependency is invalid.");
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
            if (resolvedDependency == null || resolvedDependency.version().getFileUrl() == null
                    || resolvedDependency.version().getFileUrl().isBlank()) {
                throw new IOException("A selected bundle dependency is no longer public and approved.");
            }

            byte[] fileData = archiveSupport.downloadApproved(resolvedDependency.version());
            String originalFilename = archiveSupport.extractOriginalFilename(resolvedDependency.version().getFileUrl());
            zos.putNextEntry(new ZipEntry(originalFilename));
            zos.write(fileData);
            zos.closeEntry();
        }
    }
}
