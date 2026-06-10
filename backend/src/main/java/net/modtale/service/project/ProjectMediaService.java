package net.modtale.service.project;

import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ProjectMediaOperationException;
import net.modtale.exception.StorageUploadException;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.media.MediaUploadService;
import net.modtale.service.security.FileValidationService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProjectMediaService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;
    private final ProjectMutationGuard projectMutationGuard;
    private final MediaUploadService mediaUploadService;
    private final ProjectDeletionService projectDeletionService;
    private final FileValidationService fileValidationService;
    private final int maxGalleryImages;

    public ProjectMediaService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ProjectAccessService projectAccessService,
            ProjectMutationGuard projectMutationGuard,
            MediaUploadService mediaUploadService,
            ProjectDeletionService projectDeletionService,
            FileValidationService fileValidationService,
            AppLimitProperties limitProperties
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.projectAccessService = projectAccessService;
        this.projectMutationGuard = projectMutationGuard;
        this.mediaUploadService = mediaUploadService;
        this.projectDeletionService = projectDeletionService;
        this.fileValidationService = fileValidationService;
        this.maxGalleryImages = limitProperties.maxGalleryImagesPerProject();
    }

    public void updateProjectImage(String id, MultipartFile file, User user, boolean isBanner) {
        String permission = isBanner ? "PROJECT_EDIT_BANNER" : "PROJECT_EDIT_ICON";
        Project project = projectAccessService.requireProjectPermission(id, user, permission,
                "You do not have permission to update this project's image.");
        projectMutationGuard.ensureEditable(project);

        try {
            String currentUrl = isBanner ? project.getBannerUrl() : project.getImageUrl();
            String publicUrl = mediaUploadService.uploadPublicUrl(
                    file,
                    "images",
                    isBanner ? fileValidationService::validateBanner : fileValidationService::validateIcon,
                    () -> {
                        if (currentUrl != null && !currentUrl.contains("default.png") && !currentUrl.contains("placeholder") && !currentUrl.contains("favicon")) {
                            projectDeletionService.deleteStoredFile(currentUrl);
                        }
                    }
            );

            if (isBanner) {
                project.setBannerUrl(publicUrl);
            } else {
                project.setImageUrl(publicUrl);
            }

            projectRepository.save(project);
            projectService.evictProjectCache(project);
        } catch (StorageUploadException ex) {
            throw new ProjectMediaOperationException(ex.getMessage(), ex);
        }
    }

    public void addGalleryImage(String id, MultipartFile file, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_GALLERY_ADD",
                "You do not have permission to upload gallery images for this project.");
        projectMutationGuard.ensureEditable(project);
        if (project.getGalleryImages().size() >= maxGalleryImages) {
            throw new InvalidProjectRequestException("This project has already reached the gallery image limit of " + maxGalleryImages + ".");
        }

        try {
            project.getGalleryImages().add(mediaUploadService.uploadPublicUrl(file, "gallery", fileValidationService::validateGalleryImage));
            projectRepository.save(project);
            projectService.evictProjectCache(project);
        } catch (StorageUploadException ex) {
            throw new ProjectMediaOperationException(ex.getMessage(), ex);
        }
    }

    public void removeGalleryImage(String id, String imageUrl, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_GALLERY_REMOVE",
                "You do not have permission to remove gallery images from this project.");
        projectMutationGuard.ensureEditable(project);
        project.getGalleryImages().remove(imageUrl);
        projectDeletionService.deleteStoredFile(imageUrl);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }
}
