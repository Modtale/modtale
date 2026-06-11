package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import net.modtale.service.security.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectRetentionServiceTest {

    private ProjectRetentionService service;
    private ProjectService projectService;
    private AccessControlService accessControlService;
    private ProjectDeletionService projectDeletionService;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        accessControlService = mock(AccessControlService.class);
        projectDeletionService = mock(ProjectDeletionService.class);
        ProjectAccessService projectAccessService = new ProjectAccessService(projectService, accessControlService);
        service = new ProjectRetentionService(projectAccessService, new ProjectMutationGuard(), projectDeletionService);
    }

    @Test
    void softDeleteProjectResolvesPermissionsAndDelegatesToDeletionService() {
        Project project = project("project-1", ProjectStatus.PUBLISHED);
        User user = user("user-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_DELETE")).thenReturn(true);

        service.softDeleteProject("project-1", user);

        verify(projectDeletionService).softDelete(project);
    }

    @Test
    void hardDeleteRejectsProjectsOutsideTheDeletedState() {
        Project project = project("project-1", ProjectStatus.PUBLISHED);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.hardDelete(project)
        );

        assertEquals("Project must be in DELETED state.", error.getMessage());
    }

    @Test
    void restoreValidatesTargetStatusBeforeDelegating() {
        Project project = project("project-1", ProjectStatus.DELETED);

        service.restore(project, ProjectStatus.UNLISTED);

        verify(projectDeletionService).restore(project, ProjectStatus.UNLISTED);
    }

    @Test
    void restoreSupportsPrivateStatus() {
        Project project = project("project-1", ProjectStatus.DELETED);

        service.restore(project, ProjectStatus.PRIVATE);

        verify(projectDeletionService).restore(project, ProjectStatus.PRIVATE);
    }

    @Test
    void restoreRejectsInvalidTargetStatuses() {
        Project project = project("project-1", ProjectStatus.DELETED);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.restore(project, ProjectStatus.DELETED)
        );

        assertEquals("Invalid status.", error.getMessage());
    }

    private static Project project(String id, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setStatus(status);
        project.setClassification(ProjectClassification.DATA);
        project.setVersions(new java.util.ArrayList<>());
        project.setGalleryImages(new java.util.ArrayList<>());
        return project;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
