package net.modtale.service.project.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.modtale.model.project.Comment;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.access.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectServiceTest {

    private ProjectService service;
    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private AccessControlService accessControlService;
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        accessControlService = mock(AccessControlService.class);
        ProjectRouteService projectRouteService = new ProjectRouteService();
        ProjectCacheService projectCacheService = new ProjectCacheService(new ConcurrentMapCacheManager("projectDetails"), projectRouteService);
        ProjectViewService projectViewService = new ProjectViewService(projectRepository, userRepository, mongoTemplate, accessControlService, projectRouteService);

        service = new ProjectService(
                projectViewService,
                projectCacheService,
                projectRouteService
        );
    }

    @Test
    void getProjectByIdReturnsNullForHiddenProjectsWithoutViewerAccess() {
        Project project = project("project-1", ProjectStatus.DRAFT, ProjectClassification.PLUGIN);
        User viewer = user("viewer-1", "ItsNeil17");

        when(projectRepository.findViewerDetailById("project-1")).thenReturn(Optional.of(project));
        when(accessControlService.hasEditPermission(project, viewer)).thenReturn(false);
        when(accessControlService.canReadProject(project, viewer)).thenReturn(false);

        assertNull(service.getProjectById("project-1", viewer));
    }

    @Test
    void getProjectByIdReturnsHiddenProjectsWhenViewerHasReadPermission() {
        Project project = project("project-1", ProjectStatus.DRAFT, ProjectClassification.PLUGIN);
        User viewer = user("viewer-1", "ItsNeil17");

        when(projectRepository.findViewerDetailById("project-1")).thenReturn(Optional.of(project));
        when(accessControlService.hasEditPermission(project, viewer)).thenReturn(false);
        when(accessControlService.canReadProject(project, viewer)).thenReturn(true);

        Project resolved = service.getProjectById("project-1", viewer);

        assertNotNull(resolved);
        assertEquals("project-1", resolved.getId());
    }

    @Test
    void getPublicProjectByIdFiltersVersionsCommentsAndAddsAuthorName() {
        Project project = project("project-1", ProjectStatus.PUBLISHED, ProjectClassification.MODPACK);
        project.setAllowComments(false);
        project.setComments(new ArrayList<>(List.of(new Comment())));

        ProjectVersion approved = new ProjectVersion();
        approved.setVersionNumber("1.0.0");
        approved.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        approved.setScanResult(new ScanResult(ScanStatus.CLEAN, 0, List.of()));

        ProjectVersion pending = new ProjectVersion();
        pending.setVersionNumber("1.1.0");
        pending.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        pending.setScanResult(new ScanResult(ScanStatus.SCANNING, 0, List.of()));

        project.setVersions(new ArrayList<>(List.of(approved, pending)));

        User author = user("author-1", "ItsNeil17");
        when(projectRepository.findPublicDetailById("project-1")).thenReturn(Optional.of(project));
        when(mongoTemplate.findOne(any(Query.class), eq(User.class))).thenReturn(author);
        when(accessControlService.isPubliclyReadable(project)).thenReturn(true);

        Project resolved = service.getPublicProjectById("project-1");

        assertNotNull(resolved);
        assertEquals("ItsNeil17", resolved.getAuthor());
        assertEquals(1, resolved.getVersions().size());
        assertEquals("1.0.0", resolved.getVersions().getFirst().getVersionNumber());
        assertNull(resolved.getVersions().getFirst().getScanResult());
        assertTrue(resolved.getComments().isEmpty());
    }

    @Test
    void getProjectLinkUsesWorldRouteForSaveProjects() {
        Project project = project("project-1", ProjectStatus.PUBLISHED, ProjectClassification.SAVE);
        project.setSlug("sky-world");

        assertEquals("/world/sky-world", service.getProjectLink(project));
    }

    @Test
    void getProjectLinkFallsBackToGeneratedHandlesWhenSlugIsMissing() {
        Project project = project("project-1", ProjectStatus.PUBLISHED, ProjectClassification.PLUGIN);
        project.setSlug(null);
        project.setTitle("Lock In LevelingCore Deluxe");

        assertEquals("/mod/lock-in-levelingcore-deluxe~project-1", service.getProjectLink(project));
    }

    private static Project project(String id, ProjectStatus status, ProjectClassification classification) {
        Project project = new Project();
        project.setId(id);
        project.setTitle("LevelingCore");
        project.setAuthorId("author-1");
        project.setAuthor("ItsNeil17");
        project.setStatus(status);
        project.setClassification(classification);
        project.setVersions(new ArrayList<>());
        return project;
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
