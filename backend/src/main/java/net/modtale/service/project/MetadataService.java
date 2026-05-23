package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.security.FileValidationService;
import net.modtale.service.security.SanitizationService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class MetadataService {

    private static final List<ProjectClassification> MUTABLE_CLASSIFICATIONS = List.of(
            ProjectClassification.PLUGIN,
            ProjectClassification.DATA,
            ProjectClassification.ART
    );

    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectService projectService;
    @Autowired private ValidationService validationService;
    @Autowired private AccessControlService accessControlService;
    @Autowired private LifecycleService lifecycleService;
    @Autowired private SanitizationService sanitizer;
    @Autowired private StorageService storageService;
    @Autowired private FileValidationService fileValidationService;

    @Value("${app.limits.max-gallery-images-per-project:20}")
    private int maxGalleryImages;

    public void updateMetadata(String id, Project updated, User user) {
        Project existing = projectService.getRawProjectById(id);
        if (existing == null || !accessControlService.hasProjectPermission(existing, user, "PROJECT_EDIT_METADATA")) throw new SecurityException("Denied.");
        lifecycleService.ensureEditable(existing);

        if (updated.getClassification() != null && updated.getClassification() != existing.getClassification()) {
            if (!MUTABLE_CLASSIFICATIONS.contains(existing.getClassification())) {
                throw new IllegalArgumentException("This project type cannot be changed.");
            }
            if (!MUTABLE_CLASSIFICATIONS.contains(updated.getClassification())) {
                throw new IllegalArgumentException("Projects cannot be changed to this type.");
            }
            existing.setClassification(updated.getClassification());
        }

        if (updated.getTags() != null) existing.setTags(validationService.validateTags(updated.getTags()));
        if (updated.getRepositoryUrl() != null && !updated.getRepositoryUrl().isEmpty()) validationService.validateRepositoryUrl(updated.getRepositoryUrl());
        existing.setTitle(sanitizer.sanitizePlainText(updated.getTitle()));
        existing.setDescription(sanitizer.sanitizePlainText(updated.getDescription()));
        if (updated.getAbout() != null) existing.setAbout(updated.getAbout());
        existing.setCategories(updated.getCategories());

        if (updated.getSlug() != null) {
            String newSlug = updated.getSlug().toLowerCase();
            if (newSlug.isEmpty()) existing.setSlug(null);
            else if (!newSlug.equals(existing.getSlug())) {
                validationService.validateSlug(newSlug);
                if (projectRepository.existsBySlug(newSlug)) throw new IllegalArgumentException("Slug taken.");
                existing.setSlug(newSlug);
            }
        }

        if ("MODPACK".equals(existing.getClassification())) existing.setLicense(null);
        else existing.setLicense(updated.getLicense());

        existing.setRepositoryUrl(updated.getRepositoryUrl());
        existing.setTypes(updated.getTypes());
        existing.setAllowModpacks(updated.isAllowModpacks());
        existing.setAllowComments(updated.isAllowComments());
        existing.setHmWikiEnabled(updated.isHmWikiEnabled());
        existing.setHmWikiSlug(updated.getHmWikiSlug() != null ? updated.getHmWikiSlug().trim() : null);
        if (updated.getLinks() != null) existing.setLinks(updated.getLinks());
        if (updated.getImageUrl() != null) existing.setImageUrl(updated.getImageUrl());

        projectRepository.save(existing);
        projectService.evictProjectCache(existing);
    }

    public void updateProjectImage(String id, MultipartFile file, User user, boolean isBanner) throws IOException {
        Project project = projectService.getRawProjectById(id);
        String perm = isBanner ? "PROJECT_EDIT_BANNER" : "PROJECT_EDIT_ICON";
        if (project == null || !accessControlService.hasProjectPermission(project, user, perm)) throw new SecurityException("Permission denied.");
        lifecycleService.ensureEditable(project);

        String currentUrl = isBanner ? project.getBannerUrl() : project.getImageUrl();
        if (currentUrl != null && !currentUrl.contains("default.png") && !currentUrl.contains("placeholder") && !currentUrl.contains("favicon")) {
            try { storageService.deleteFile(currentUrl); } catch (Exception ignore) {}
        }

        String path = storageService.upload(file, "images");
        String publicUrl = storageService.getPublicUrl(path);

        if (isBanner) project.setBannerUrl(publicUrl);
        else project.setImageUrl(publicUrl);

        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void addGalleryImage(String id, MultipartFile file, User user) throws IOException {
        Project project = projectService.getRawProjectById(id);
        if (project != null && accessControlService.hasProjectPermission(project, user, "PROJECT_GALLERY_ADD")) {
            lifecycleService.ensureEditable(project);
            if (project.getGalleryImages().size() >= maxGalleryImages) throw new IllegalStateException("Maximum gallery images reached.");

            fileValidationService.validateGalleryImage(file);
            String path = storageService.upload(file, "gallery");
            project.getGalleryImages().add(storageService.getPublicUrl(path));

            projectRepository.save(project);
            projectService.evictProjectCache(project);
        } else throw new SecurityException("Permission denied.");
    }

    public void removeGalleryImage(String id, String imageUrl, User user) {
        Project project = projectService.getRawProjectById(id);
        if (project != null && accessControlService.hasProjectPermission(project, user, "PROJECT_GALLERY_REMOVE")) {
            lifecycleService.ensureEditable(project);
            project.getGalleryImages().remove(imageUrl);
            storageService.deleteFile(imageUrl);
            projectRepository.save(project);
            projectService.evictProjectCache(project);
        } else throw new SecurityException("Permission denied.");
    }
}
