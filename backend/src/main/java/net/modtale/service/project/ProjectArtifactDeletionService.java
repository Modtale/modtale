package net.modtale.service.project;

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

    public void deleteProjectMedia(Project project) {
        deleteStoredFile(project.getImageUrl());
        deleteStoredFile(project.getBannerUrl());
        if (project.getGalleryImages() != null) {
            project.getGalleryImages().forEach(this::deleteStoredFile);
            project.getGalleryImages().clear();
        }
        project.setImageUrl(null);
        project.setBannerUrl(null);
    }
}
