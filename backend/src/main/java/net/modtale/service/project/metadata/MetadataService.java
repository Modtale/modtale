package net.modtale.service.project.metadata;

import java.util.List;
import java.util.regex.Pattern;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectLicenseSupport;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.access.ProjectMutationGuard;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.project.validation.ValidationService;
import net.modtale.service.security.validation.SanitizationService;
import org.springframework.stereotype.Service;

@Service
public class MetadataService {

    private static final List<ProjectClassification> MUTABLE_CLASSIFICATIONS = List.of(
            ProjectClassification.PLUGIN,
            ProjectClassification.DATA,
            ProjectClassification.ART
    );
    private static final Pattern GALLERY_CAROUSEL_MARKER_PATTERN = Pattern.compile("\\{\\{\\s*gallery-carousel\\s*\\}\\}", Pattern.CASE_INSENSITIVE);

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ValidationService validationService;
    private final ProjectAccessService projectAccessService;
    private final ProjectMutationGuard projectMutationGuard;
    private final SanitizationService sanitizer;

    public MetadataService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ValidationService validationService,
            ProjectAccessService projectAccessService,
            ProjectMutationGuard projectMutationGuard,
            SanitizationService sanitizer
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.validationService = validationService;
        this.projectAccessService = projectAccessService;
        this.projectMutationGuard = projectMutationGuard;
        this.sanitizer = sanitizer;
    }

    public void updateMetadata(String id, Project updated, User user) {
        Project existing = projectAccessService.requireProjectPermission(id, user, "PROJECT_EDIT_METADATA",
                "You do not have permission to edit this project's metadata.");
        projectMutationGuard.ensureEditable(existing);

        if (updated.getClassification() != null && updated.getClassification() != existing.getClassification()) {
            if (!MUTABLE_CLASSIFICATIONS.contains(existing.getClassification())) {
                throw new InvalidProjectRequestException("This project type cannot be changed.");
            }
            if (!MUTABLE_CLASSIFICATIONS.contains(updated.getClassification())) {
                throw new InvalidProjectRequestException("Projects cannot be changed to that type.");
            }
            existing.setClassification(updated.getClassification());
        }

        if (updated.getTags() != null) existing.setTags(validationService.validateTags(updated.getTags()));
        if (updated.getRepositoryUrl() != null && !updated.getRepositoryUrl().isEmpty()) validationService.validateRepositoryUrl(updated.getRepositoryUrl());
        existing.setTitle(sanitizer.sanitizePlainText(updated.getTitle()));
        existing.setDescription(sanitizer.sanitizePlainText(updated.getDescription()));
        if (updated.getAbout() != null) {
            validateSingleGalleryCarouselMarker(updated.getAbout());
            existing.setAbout(updated.getAbout());
        }
        existing.setCategories(updated.getCategories());

        if (updated.getSlug() != null) {
            String newSlug = updated.getSlug().toLowerCase();
            if (newSlug.isEmpty()) existing.setSlug(null);
            else if (!newSlug.equals(existing.getSlug())) {
                validationService.validateSlug(newSlug);
                if (projectRepository.existsBySlug(newSlug)) {
                    throw new InvalidProjectRequestException("That project slug is already taken.");
                }
                existing.setSlug(newSlug);
            }
        }

        if (existing.getClassification() == ProjectClassification.MODPACK) {
            existing.setLicense(null);
            existing.setCustomLicenseOpenSource(false);
        } else {
            existing.setLicense(updated.getLicense());
            existing.setCustomLicenseOpenSource(ProjectLicenseSupport.shouldPersistCustomOpenSource(
                    updated.getLicense(),
                    updated.isCustomLicenseOpenSource()
            ));
        }

        existing.setRepositoryUrl(updated.getRepositoryUrl());
        existing.setTypes(updated.getTypes());
        existing.setAllowModpacks(updated.isAllowModpacks());
        existing.setAllowComments(updated.isAllowComments());
        existing.setHmWikiEnabled(updated.isHmWikiEnabled());
        existing.setHmWikiSlug(updated.getHmWikiSlug() != null ? updated.getHmWikiSlug().trim() : null);
        existing.setGalleryCarouselEnabled(updated.isGalleryCarouselEnabled());
        if (updated.getLinks() != null) existing.setLinks(updated.getLinks());
        if (updated.getImageUrl() != null) existing.setImageUrl(updated.getImageUrl());

        projectRepository.save(existing);
        projectService.evictProjectCache(existing);
    }

    private void validateSingleGalleryCarouselMarker(String about) {
        int markerCount = 0;
        var matcher = GALLERY_CAROUSEL_MARKER_PATTERN.matcher(about);
        while (matcher.find()) {
            markerCount += 1;
            if (markerCount > 1) {
                throw new InvalidProjectRequestException("Use {{gallery-carousel}} only once in the project description.");
            }
        }
    }
}
