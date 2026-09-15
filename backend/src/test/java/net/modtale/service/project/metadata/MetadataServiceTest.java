package net.modtale.service.project.metadata;

import java.util.ArrayList;
import java.util.List;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.access.ProjectMutationGuard;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.project.validation.ValidationService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.security.validation.SanitizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataServiceTest {

    private MetadataService service;
    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private ValidationService validationService;
    private AccessControlService accessControlService;
    private ProjectAccessService projectAccessService;
    private SanitizationService sanitizationService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        validationService = mock(ValidationService.class);
        accessControlService = mock(AccessControlService.class);
        projectAccessService = new ProjectAccessService(projectService, accessControlService);
        sanitizationService = mock(SanitizationService.class);

        service = new MetadataService(
                projectRepository,
                projectService,
                validationService,
                projectAccessService,
                new ProjectMutationGuard(),
                sanitizationService
        );
    }

    @Test
    void updateMetadataAllowsMutableClassificationChangesAndSanitizesPrimaryFields() {
        Project existing = new Project();
        existing.setId("project-1");
        existing.setClassification(ProjectClassification.DATA);
        existing.setSlug("sky-tools");
        existing.setTags(new ArrayList<>(List.of("old")));

        Project updated = new Project();
        updated.setClassification(ProjectClassification.ART);
        updated.setTitle("Raw Title");
        updated.setDescription("Raw Description");
        updated.setTags(List.of("magic"));
        updated.setSlug("new-slug");
        updated.setLicense("MIT");
        updated.setCustomLicenseOpenSource(true);

        User user = new User();
        user.setId("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(existing);
        when(accessControlService.hasProjectPermission(existing, user, "PROJECT_EDIT_METADATA")).thenReturn(true);
        when(validationService.validateTags(List.of("magic"))).thenReturn(List.of("magic"));
        when(sanitizationService.sanitizePlainText("Raw Title")).thenReturn("Clean Title");
        when(sanitizationService.sanitizePlainText("Raw Description")).thenReturn("Clean Description");
        when(projectRepository.existsBySlug("new-slug")).thenReturn(false);

        service.updateMetadata("project-1", updated, user);

        assertEquals(ProjectClassification.ART, existing.getClassification());
        assertEquals("Clean Title", existing.getTitle());
        assertEquals("Clean Description", existing.getDescription());
        assertEquals(List.of("magic"), existing.getTags());
        assertEquals("new-slug", existing.getSlug());
        assertEquals("MIT", existing.getLicense());
        assertFalse(existing.isCustomLicenseOpenSource());
        verify(validationService).validateSlug("new-slug");
        verify(projectRepository).save(existing);
        verify(projectService).evictProjectCache(existing);
    }

    @Test
    void updateMetadataPersistsOpenSourceFlagForCustomLicenses() {
        Project existing = new Project();
        existing.setId("project-1");
        existing.setClassification(ProjectClassification.DATA);

        Project updated = new Project();
        updated.setTitle("Raw Title");
        updated.setDescription("Raw Description");
        updated.setLicense("Sky Public License");
        updated.setCustomLicenseOpenSource(true);

        User user = new User();
        user.setId("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(existing);
        when(accessControlService.hasProjectPermission(existing, user, "PROJECT_EDIT_METADATA")).thenReturn(true);
        when(sanitizationService.sanitizePlainText("Raw Title")).thenReturn("Clean Title");
        when(sanitizationService.sanitizePlainText("Raw Description")).thenReturn("Clean Description");

        service.updateMetadata("project-1", updated, user);

        assertEquals("Sky Public License", existing.getLicense());
        assertTrue(existing.isCustomLicenseOpenSource());
        verify(projectRepository).save(existing);
    }

    @Test
    void updateMetadataRejectsMultipleGalleryCarouselMarkers() {
        Project existing = new Project();
        existing.setId("project-1");
        existing.setClassification(ProjectClassification.DATA);

        Project updated = new Project();
        updated.setTitle("Raw Title");
        updated.setDescription("Raw Description");
        updated.setAbout("Intro\n\n{{gallery-carousel}}\n\nOutro\n\n{{ gallery-carousel }}");

        User user = new User();
        user.setId("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(existing);
        when(accessControlService.hasProjectPermission(existing, user, "PROJECT_EDIT_METADATA")).thenReturn(true);
        when(sanitizationService.sanitizePlainText("Raw Title")).thenReturn("Clean Title");
        when(sanitizationService.sanitizePlainText("Raw Description")).thenReturn("Clean Description");

        InvalidProjectRequestException error = assertThrows(
                InvalidProjectRequestException.class,
                () -> service.updateMetadata("project-1", updated, user)
        );

        assertEquals("Use {{gallery-carousel}} only once in the project description.", error.getMessage());
        verify(projectRepository, never()).save(existing);
    }
}
