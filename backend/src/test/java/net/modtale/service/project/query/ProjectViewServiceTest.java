package net.modtale.service.project.query;

import java.util.List;
import java.util.Optional;
import net.modtale.model.dto.project.ProjectVersionChangelogDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.access.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectViewServiceTest {

    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private MongoTemplate mongoTemplate;
    private AccessControlService accessControlService;
    private ProjectRouteService projectRouteService;
    private ProjectViewService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        accessControlService = mock(AccessControlService.class);
        projectRouteService = new ProjectRouteService();
        service = new ProjectViewService(projectRepository, userRepository, mongoTemplate, accessControlService, projectRouteService);
    }

    @Test
    void getProjectByIdRedactsScanResultsForNonAdminContributors() {
        Project project = projectWithScanResult();
        User contributor = user("user-1", "ada");

        when(projectRepository.findViewerDetailById("project-1")).thenReturn(Optional.of(project));
        when(userRepository.findById("author-1")).thenReturn(Optional.of(user("author-1", "author")));
        when(accessControlService.hasEditPermission(project, contributor)).thenReturn(true);
        when(accessControlService.isAdmin(contributor)).thenReturn(false);

        Project result = service.getProjectById("project-1", contributor);

        assertNotNull(result);
        assertNull(result.getVersions().getFirst().getScanResult());
    }

    @Test
    void getProjectByIdOmitsScanResultsForAdminsOnRegularProjectReads() {
        Project project = projectWithScanResult();
        User admin = user("admin-1", "mod");

        when(projectRepository.findViewerDetailById("project-1")).thenReturn(Optional.of(project));
        when(userRepository.findById("author-1")).thenReturn(Optional.of(user("author-1", "author")));
        when(accessControlService.hasEditPermission(project, admin)).thenReturn(false);
        when(accessControlService.canReadProject(project, admin)).thenReturn(true);

        Project result = service.getProjectById("project-1", admin);

        assertNotNull(result);
        assertNull(result.getVersions().getFirst().getScanResult());
    }

    @Test
    void getPublicProjectByRouteKeyResolvesCanonicalSlugRoutes() {
        Project project = projectWithScanResult();
        project.setSlug("levelingcore");
        project.setStatus(ProjectStatus.PUBLISHED);

        when(projectRepository.findPublicDetailBySlug("levelingcore")).thenReturn(Optional.of(project));
        when(userRepository.findById("author-1")).thenReturn(Optional.of(user("author-1", "author")));
        when(accessControlService.isPubliclyReadable(project)).thenReturn(true);

        Project result = service.getPublicProjectByRouteKey("levelingcore");

        assertNotNull(result);
    }

    @Test
    void getProjectByRouteKeyPrefersExplicitLegacyIdHandles() {
        Project project = projectWithScanResult();
        project.setSlug("levelingcore");
        project.setStatus(ProjectStatus.PUBLISHED);
        User viewer = user("viewer-1", "viewer");

        when(projectRepository.findViewerDetailById("project-1")).thenReturn(Optional.of(project));
        when(userRepository.findById("author-1")).thenReturn(Optional.of(user("author-1", "author")));
        when(accessControlService.hasEditPermission(project, viewer)).thenReturn(false);
        when(accessControlService.isAdmin(viewer)).thenReturn(false);
        when(accessControlService.canReadProject(project, viewer)).thenReturn(true);

        Project result = service.getProjectByRouteKey("levelingcore~project-1", viewer);

        assertNotNull(result);
    }

    @Test
    void getVersionChangelogPageProjectsOnlyTheRequestedVersionSlice() {
        Project route = projectWithScanResult();
        route.setSlug("levelingcore");
        Project page = projectWithScanResult();
        page.setVersions(List.of(route.getVersions().getFirst()));
        AggregationResults<Project> aggregationResults = mock(AggregationResults.class);

        when(projectRepository.findPermissionSnapshotBySlug("levelingcore")).thenReturn(Optional.of(route));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("projects"), eq(Project.class))).thenReturn(aggregationResults);
        when(aggregationResults.getUniqueMappedResult()).thenReturn(page);
        when(accessControlService.canReadProject(route, null)).thenReturn(true);

        List<ProjectVersionChangelogDTO> result = service.getVersionChangelogsByRouteKey(
                "levelingcore", null, 12, 12);

        assertEquals(1, result.size());
        assertEquals("version-1", result.getFirst().id());
        assertTrue(result.getFirst().changelog() == null || result.getFirst().changelog().isEmpty());
        verify(mongoTemplate).aggregate(any(Aggregation.class), eq("projects"), eq(Project.class));
    }

    private static Project projectWithScanResult() {
        ProjectVersion version = new ProjectVersion();
        version.setId("version-1");
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        version.setScanResult(new ScanResult());

        Project project = new Project();
        project.setId("project-1");
        project.setAuthorId("author-1");
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setVersions(List.of(version));
        return project;
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
