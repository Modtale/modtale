package net.modtale.service.project.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.media.MediaUploadService;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.access.ProjectMutationGuard;
import net.modtale.service.project.lifecycle.ProjectDeletionService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.security.validation.FileValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectMediaServiceTest {

    private ProjectMediaService service;
    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private AccessControlService accessControlService;
    private ProjectAccessService projectAccessService;
    private ProjectDeletionService projectDeletionService;
    private FileValidationService fileValidationService;
    private MediaUploadService mediaUploadService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        accessControlService = mock(AccessControlService.class);
        projectAccessService = new ProjectAccessService(projectService, accessControlService);
        projectDeletionService = mock(ProjectDeletionService.class);
        fileValidationService = mock(FileValidationService.class);
        mediaUploadService = mock(MediaUploadService.class);

        service = new ProjectMediaService(
                projectRepository,
                projectService,
                projectAccessService,
                new ProjectMutationGuard(),
                mediaUploadService,
                projectDeletionService,
                fileValidationService,
                new AppLimitProperties(10, 5, 10, 5, 5, 50, 2, 10)
        );
    }

    @Test
    void addGalleryImageRejectsProjectsAtTheConfiguredLimit() throws Exception {
        Project project = new Project();
        project.setId("project-1");
        project.setGalleryImages(new ArrayList<>(List.of("one.png", "two.png")));
        User user = user("user-1");
        MockMultipartFile file = new MockMultipartFile("file", "gallery.png", "image/png", new byte[]{1, 2, 3});

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_GALLERY_ADD")).thenReturn(true);

        InvalidProjectRequestException error = assertThrows(
                InvalidProjectRequestException.class,
                () -> service.addGalleryImage("project-1", file, user)
        );

        assertEquals("This project has already reached the gallery limit of 2 items.", error.getMessage());
        verify(fileValidationService, never()).validateGalleryImage(any());
        verify(mediaUploadService, never()).uploadPublicUrl(any(), eq("gallery"), any());
    }

    @Test
    void addGalleryVideoStoresNormalizedYouTubeWatchUrls() {
        Project project = new Project();
        project.setId("project-1");
        project.setGalleryImages(new ArrayList<>());
        User user = user("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_GALLERY_ADD")).thenReturn(true);
        when(projectRepository.save(project)).thenReturn(project);

        Project updated = service.addGalleryVideo("project-1", "https://youtu.be/dQw4w9WgXcQ?si=test", user);

        assertEquals(project, updated);
        assertEquals(List.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ"), project.getGalleryImages());
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
    }

    @Test
    void addGalleryVideoRejectsNonYouTubeUrls() {
        Project project = new Project();
        project.setId("project-1");
        project.setGalleryImages(new ArrayList<>());
        User user = user("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_GALLERY_ADD")).thenReturn(true);

        InvalidProjectRequestException error = assertThrows(
                InvalidProjectRequestException.class,
                () -> service.addGalleryVideo("project-1", "https://example.com/watch?v=dQw4w9WgXcQ", user)
        );

        assertEquals("Gallery videos must be valid YouTube video URLs.", error.getMessage());
        verify(projectRepository, never()).save(project);
    }

    @Test
    void addGalleryVideoRejectsDuplicatesAfterNormalization() {
        Project project = new Project();
        project.setId("project-1");
        project.setGalleryImages(new ArrayList<>(List.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ")));
        User user = user("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_GALLERY_ADD")).thenReturn(true);

        InvalidProjectRequestException error = assertThrows(
                InvalidProjectRequestException.class,
                () -> service.addGalleryVideo("project-1", "https://www.youtube.com/embed/dQw4w9WgXcQ", user)
        );

        assertEquals("That YouTube video is already in this project gallery.", error.getMessage());
        verify(projectRepository, never()).save(project);
    }

    @Test
    void removeGalleryImageDeletesTheStoredAssetAndEvictsProjectCache() {
        Project project = new Project();
        project.setId("project-1");
        project.setGalleryImages(new ArrayList<>(List.of("https://cdn.modtale.test/gallery/a.png", "https://cdn.modtale.test/gallery/b.png")));
        project.setGalleryImageCaptions(Map.of("https://cdn.modtale.test/gallery/a.png", "Opening shot"));
        User user = user("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_GALLERY_REMOVE")).thenReturn(true);

        service.removeGalleryImage("project-1", "https://cdn.modtale.test/gallery/a.png", user);

        assertEquals(List.of("https://cdn.modtale.test/gallery/b.png"), project.getGalleryImages());
        assertFalse(project.getGalleryImageCaptions().containsKey("https://cdn.modtale.test/gallery/a.png"));
        verify(projectDeletionService).deleteStoredFile("https://cdn.modtale.test/gallery/a.png");
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
    }

    @Test
    void removeGalleryImageDoesNotDeleteYoutubeVideosFromStorage() {
        Project project = new Project();
        project.setId("project-1");
        project.setGalleryImages(new ArrayList<>(List.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ")));
        project.setGalleryImageCaptions(Map.of("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "Launch trailer"));
        User user = user("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_GALLERY_REMOVE")).thenReturn(true);
        when(projectRepository.save(project)).thenReturn(project);

        service.removeGalleryImage("project-1", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", user);

        assertEquals(List.of(), project.getGalleryImages());
        assertFalse(project.getGalleryImageCaptions().containsKey("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        verify(projectDeletionService, never()).deleteStoredFile(any());
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
    }

    @Test
    void updateGalleryImageCaptionStoresTrimmedCaptionsAndEvictsProjectCache() {
        Project project = new Project();
        project.setId("project-1");
        project.setGalleryImages(new ArrayList<>(List.of("https://cdn.modtale.test/gallery/a.png")));
        project.setGalleryImageCaptions(Map.of());
        User user = user("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_GALLERY_ADD")).thenReturn(true);
        when(projectRepository.save(project)).thenReturn(project);

        Project updated = service.updateGalleryImageCaption(
                "project-1",
                "https://cdn.modtale.test/gallery/a.png",
                "  Opening shot  ",
                user
        );

        assertEquals(project, updated);
        assertEquals("Opening shot", project.getGalleryImageCaptions().get("https://cdn.modtale.test/gallery/a.png"));
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
    }

    @Test
    void updateGalleryImageCaptionRemovesBlankCaptions() {
        Project project = new Project();
        project.setId("project-1");
        project.setGalleryImages(new ArrayList<>(List.of("https://cdn.modtale.test/gallery/a.png")));
        project.setGalleryImageCaptions(Map.of("https://cdn.modtale.test/gallery/a.png", "Opening shot"));
        User user = user("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_GALLERY_ADD")).thenReturn(true);
        when(projectRepository.save(project)).thenReturn(project);

        service.updateGalleryImageCaption("project-1", "https://cdn.modtale.test/gallery/a.png", "   ", user);

        assertFalse(project.getGalleryImageCaptions().containsKey("https://cdn.modtale.test/gallery/a.png"));
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
    }

    @Test
    void updateGalleryImageCaptionRejectsUnknownImages() {
        Project project = new Project();
        project.setId("project-1");
        project.setGalleryImages(new ArrayList<>(List.of("https://cdn.modtale.test/gallery/a.png")));
        User user = user("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_GALLERY_ADD")).thenReturn(true);

        InvalidProjectRequestException error = assertThrows(
                InvalidProjectRequestException.class,
                () -> service.updateGalleryImageCaption("project-1", "https://cdn.modtale.test/gallery/missing.png", "Missing", user)
        );

        assertEquals("That gallery image does not exist on this project.", error.getMessage());
        verify(projectRepository, never()).save(project);
        verify(projectService, never()).evictProjectCache(project);
    }

    @Test
    void updateProjectImageCleansUpExistingStoredAssetsThroughTheSharedDeletionHelper() {
        Project project = new Project();
        project.setId("project-1");
        project.setImageUrl("https://cdn.modtale.test/images/current-icon.png");
        User user = user("user-1");
        MockMultipartFile file = new MockMultipartFile("file", "icon.png", "image/png", new byte[]{1, 2, 3});

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_EDIT_ICON")).thenReturn(true);
        when(mediaUploadService.uploadPublicUrl(eq(file), eq("images"), any(), any())).thenAnswer(invocation -> {
            Runnable cleanupExisting = invocation.getArgument(3);
            cleanupExisting.run();
            return "https://cdn.modtale.test/images/new-icon.png";
        });

        service.updateProjectImage("project-1", file, user, false);

        assertEquals("https://cdn.modtale.test/images/new-icon.png", project.getImageUrl());
        verify(projectDeletionService).deleteStoredFile("https://cdn.modtale.test/images/current-icon.png");
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
