package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectViewServiceTest {

    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private MongoTemplate mongoTemplate;
    private AccessControlService accessControlService;
    private ProjectViewService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        accessControlService = mock(AccessControlService.class);
        service = new ProjectViewService(projectRepository, userRepository, mongoTemplate, accessControlService);
    }

    @Test
    void getProjectByIdRedactsScanResultsForNonAdminContributors() {
        Project project = projectWithScanResult();
        User contributor = user("user-1", "ada");

        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(userRepository.findById("author-1")).thenReturn(Optional.of(user("author-1", "author")));
        when(accessControlService.hasEditPermission(project, contributor)).thenReturn(true);
        when(accessControlService.isAdmin(contributor)).thenReturn(false);

        Project result = service.getProjectById("project-1", contributor);

        assertNotNull(result);
        assertNull(result.getVersions().getFirst().getScanResult());
    }

    @Test
    void getProjectByIdRetainsScanResultsForAdmins() {
        Project project = projectWithScanResult();
        User admin = user("admin-1", "mod");

        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(userRepository.findById("author-1")).thenReturn(Optional.of(user("author-1", "author")));
        when(accessControlService.hasEditPermission(project, admin)).thenReturn(false);
        when(accessControlService.isAdmin(admin)).thenReturn(true);

        Project result = service.getProjectById("project-1", admin);

        assertNotNull(result);
        assertNotNull(result.getVersions().getFirst().getScanResult());
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
