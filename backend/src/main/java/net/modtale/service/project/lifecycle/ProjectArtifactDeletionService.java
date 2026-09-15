package net.modtale.service.project.lifecycle;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.storage.StorageService;
import org.springframework.stereotype.Service;

@Service
public class ProjectArtifactDeletionService {

    private final StorageService storageService;

    public ProjectArtifactDeletionService(StorageService storageService) {
        this.storageService = storageService;
    }

    public void deleteVersionFile(ProjectVersion version) {
        if (version != null) {
            deleteStoredFile(version.getFileUrl());
            if (version.getOverrideFileUrl() != null
                    && !version.getOverrideFileUrl().equals(version.getFileUrl())) {
                deleteStoredFile(version.getOverrideFileUrl());
            }
        }
    }

    public void deleteVersionFile(String fileUrl) {
        deleteStoredFile(fileUrl);
    }

    public void deleteStoredFile(String fileUrl) {
        // Best-effort cleanup: storage errors are already logged by StorageService.
        if (fileUrl != null) {
            storageService.deleteFile(fileUrl);
        }
    }

    public void deleteProjectMediaFile(Project project, String location) {
        if (project == null) return;
        var remaining = new java.util.ArrayList<String>();
        remaining.add(project.getImageUrl()); remaining.add(project.getBannerUrl());
        if (project.getGalleryImages() != null) remaining.addAll(project.getGalleryImages());
        storageService.deleteOwnedProjectMedia(project.getId(), location, remaining);
    }

    public void deleteProjectMedia(Project project) {
        var removed = new java.util.ArrayList<String>();
        removed.add(project.getImageUrl()); removed.add(project.getBannerUrl());
        if (project.getGalleryImages() != null) removed.addAll(project.getGalleryImages());
        project.setImageUrl(null); project.setBannerUrl(null);
        project.setGalleryImages(new java.util.ArrayList<>());
        for (String location : removed) deleteProjectMediaFile(project, location);
    }
}
