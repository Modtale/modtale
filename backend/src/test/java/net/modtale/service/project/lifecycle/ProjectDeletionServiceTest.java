package net.modtale.service.project.lifecycle;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.ApiKey;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectDeletionServiceTest {

    private ProjectDeletionService service;
    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private TrackingService trackingService;
    private ScoringService scoringService;
    private StorageService storageService;
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        trackingService = mock(TrackingService.class);
        scoringService = mock(ScoringService.class);
        storageService = mock(StorageService.class);
        mongoTemplate = mock(MongoTemplate.class);
        ProjectArtifactDeletionService projectArtifactDeletionService = new ProjectArtifactDeletionService(storageService);
        service = new ProjectDeletionService(
                projectRepository,
                projectService,
                trackingService,
                scoringService,
                projectArtifactDeletionService,
                mongoTemplate
        );
    }

    @Test
    void softDeleteMarksPublishedProjectsDeletedAndTracksRemoval() {
        Project project = editableProject("project-1", ProjectClassification.DATA, ProjectStatus.PUBLISHED);

        service.softDelete(project);

        assertEquals(ProjectStatus.DELETED, project.getStatus());
        assertNotNull(project.getDeletedAt());
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        verify(trackingService).logDeletedProject("project-1");
    }

    @Test
    void hardDeleteScrubsProjectsThatAreStillNeededForDependencies() {
        Project project = editableProject("project-1", ProjectClassification.DATA, ProjectStatus.DELETED);
        project.setTitle("Sky Tools");
        project.setDescription("Original description");
        project.setAbout("Original about");
        project.setSlug("sky-tools");
        project.setImageUrl("https://cdn.modtale.net/icon.png");
        project.setBannerUrl("https://cdn.modtale.net/banner.png");
        project.setGalleryImages(new ArrayList<>(List.of("https://cdn.modtale.net/one.png", "https://cdn.modtale.net/two.png")));
        project.setTeamMembers(new ArrayList<>(List.of(new Project.ProjectMember("user-1", "role-1"))));
        project.setTeamInvites(new ArrayList<>(List.of(new Project.ProjectMember("user-2", "role-2"))));
        project.setProjectRoles(new ArrayList<>(List.of(new Project.ProjectRole("role-1", "Admin", "#fff", Set.of(ApiKey.ApiPermission.PROJECT_EDIT_METADATA)))));
        project.setComments(new ArrayList<>());
        project.setTags(new ArrayList<>(List.of("magic")));
        project.setDeletedAt(LocalDateTime.now());

        when(projectRepository.findByDependency("project-1")).thenReturn(List.of(editableProject("dependent-1", ProjectClassification.DATA, ProjectStatus.PUBLISHED)));
        when(projectRepository.save(project)).thenReturn(project);

        service.hardDelete(project);

        assertEquals("Deleted Project", project.getTitle());
        assertEquals("This project has been deleted.", project.getDescription());
        assertEquals("This project was deleted by the author but is retained for dependency resolution.", project.getAbout());
        assertNull(project.getSlug());
        assertNull(project.getImageUrl());
        assertNull(project.getBannerUrl());
        assertTrue(project.getGalleryImages().isEmpty());
        assertTrue(project.getTeamMembers().isEmpty());
        assertTrue(project.getTeamInvites().isEmpty());
        assertTrue(project.getProjectRoles().isEmpty());
        assertTrue(project.getTags().isEmpty());
        assertNull(project.getDeletedAt());

        verify(storageService).deleteFile("https://cdn.modtale.net/icon.png");
        verify(storageService).deleteFile("https://cdn.modtale.net/banner.png");
        verify(storageService).deleteFile("https://cdn.modtale.net/one.png");
        verify(storageService).deleteFile("https://cdn.modtale.net/two.png");
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        verify(projectRepository, never()).delete(project);
        verify(trackingService, never()).deleteProjectAnalytics("project-1");
    }

    @Test
    void hardDeleteRemovesArtifactsAnalyticsAndProjectRecordsWhenNoDependentsRemain() {
        Project project = editableProject("project-1", ProjectClassification.DATA, ProjectStatus.DELETED);
        project.setImageUrl("https://cdn.modtale.net/icon.png");
        project.setBannerUrl("https://cdn.modtale.net/banner.png");
        project.setGalleryImages(new ArrayList<>(List.of("https://cdn.modtale.net/one.png")));
        project.setModIds(new ArrayList<>(List.of("dep-2")));

        ProjectVersion version = version("1.0.0");
        version.setFileUrl("files/project-1/main.jar");
        version.setDependencies(List.of(new ProjectDependency("dep-1", "Dependency One", "2.0.0")));
        project.setVersions(new ArrayList<>(List.of(version)));

        Project orphan = editableProject("dep-1", ProjectClassification.DATA, ProjectStatus.DELETED);

        when(projectRepository.findByDependency("project-1")).thenReturn(List.of());
        when(projectService.getRawProjectById("dep-1")).thenReturn(orphan);
        when(projectService.getRawProjectById("dep-2")).thenReturn(null);
        when(projectRepository.findByDependency("dep-1")).thenReturn(List.of());

        service.hardDelete(project);

        verify(trackingService).deleteProjectAnalytics("project-1");
        verify(storageService).deleteFile("files/project-1/main.jar");
        verify(storageService).deleteFile("https://cdn.modtale.net/icon.png");
        verify(storageService).deleteFile("https://cdn.modtale.net/banner.png");
        verify(storageService).deleteFile("https://cdn.modtale.net/one.png");
        verify(mongoTemplate, times(2)).updateMulti(any(Query.class), any(Update.class), eq(net.modtale.model.user.User.class));
        verify(projectRepository).delete(project);
        verify(projectService).evictProjectCache(project);

        verify(trackingService).deleteProjectAnalytics("dep-1");
        verify(projectRepository).delete(orphan);
        verify(projectService).evictProjectCache(orphan);
    }

    private static Project editableProject(String id, ProjectClassification classification, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setSlug("sky-tools");
        project.setTitle("Sky Tools");
        project.setClassification(classification);
        project.setAuthorId("author-1");
        project.setAuthor("Ada");
        project.setStatus(status);
        project.setVersions(new ArrayList<>());
        project.setTags(new ArrayList<>());
        project.setGalleryImages(new ArrayList<>());
        project.setTeamMembers(new ArrayList<>());
        project.setTeamInvites(new ArrayList<>());
        project.setProjectRoles(new ArrayList<>());
        project.setComments(new ArrayList<>());
        return project;
    }

    private static ProjectVersion version(String versionNumber) {
        ProjectVersion version = new ProjectVersion();
        version.setId("version-" + versionNumber);
        version.setVersionNumber(versionNumber);
        return version;
    }
}
