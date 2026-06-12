package net.modtale.service.admin;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.communication.ProjectNotificationService;
import net.modtale.service.project.LifecycleService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.project.ProjectVersionAccessService;
import net.modtale.service.security.SecurityIssueAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectReviewDecisionServiceTest {

    private ProjectReviewDecisionService service;
    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private ProjectService projectService;
    private LifecycleService lifecycleService;
    private NotificationService notificationService;
    private ProjectNotificationService projectNotificationService;
    private SecurityIssueAnalysisService securityIssueAnalysisService;
    private ProjectVersionAccessService projectVersionAccessService;
    private AdminAuditLogger adminAuditLogger;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        projectService = mock(ProjectService.class);
        lifecycleService = mock(LifecycleService.class);
        notificationService = mock(NotificationService.class);
        projectNotificationService = mock(ProjectNotificationService.class);
        securityIssueAnalysisService = mock(SecurityIssueAnalysisService.class);
        projectVersionAccessService = mock(ProjectVersionAccessService.class);
        adminAuditLogger = mock(AdminAuditLogger.class);

        ProjectReviewTransitionService transitionService = new ProjectReviewTransitionService(
                projectRepository,
                projectService,
                lifecycleService,
                mock(ScoringService.class),
                securityIssueAnalysisService,
                projectVersionAccessService
        );
        ProjectReviewEffectService effectService = new ProjectReviewEffectService(
                userRepository,
                notificationService,
                projectNotificationService,
                adminAuditLogger
        );
        service = new ProjectReviewDecisionService(transitionService, effectService);
    }

    @Test
    void approveVersionUpdatesStateBroadcastsAndAudits() {
        User admin = user("admin-1", "Ada");
        Project project = project("project-1", "author-1");
        ProjectVersion version = version("version-1", "1.0.0");
        version.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        version.setRejectionReason("Old reason");
        version.setScheduledPublishDate("2026-06-09T12:00:00");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(projectVersionAccessService.requireById(eq(project), eq("version-1"), any())).thenReturn(version);

        service.approveVersion(admin, "project-1", "version-1");

        assertEquals(ProjectVersion.ReviewStatus.APPROVED, version.getReviewStatus());
        assertNull(version.getRejectionReason());
        assertNull(version.getScheduledPublishDate());
        assertNotNull(project.getUpdatedAt());

        verify(securityIssueAnalysisService).pruneApprovedScanResults(project);
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        verify(projectNotificationService).notifyUpdates(project, "1.0.0");
        verify(projectNotificationService).notifyDependents(project, "1.0.0");
        verify(adminAuditLogger).logAction("admin-1", "APPROVE_VERSION", "project-1", "VERSION", "VerID: version-1");
    }

    @Test
    void rejectVersionNotifiesAuthorAndAudits() {
        User admin = user("admin-1", "Ada");
        User author = user("author-1", "Bea");
        Project project = project("project-1", "author-1");
        ProjectVersion version = version("version-1", "1.2.0");
        version.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        version.setScheduledPublishDate("2026-06-09T12:00:00");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(projectVersionAccessService.requireById(eq(project), eq("version-1"), any())).thenReturn(version);
        when(userRepository.findById("author-1")).thenReturn(Optional.of(author));

        service.rejectVersion(admin, "project-1", "version-1", "Missing metadata");

        assertEquals(ProjectVersion.ReviewStatus.REJECTED, version.getReviewStatus());
        assertEquals("Missing metadata", version.getRejectionReason());
        assertNull(version.getScheduledPublishDate());

        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        verify(notificationService).sendNotifcation(
                java.util.List.of("author-1"),
                "Version Rejected",
                "Version 1.2.0 of Sky Tools was rejected. Reason: Missing metadata",
                URI.create("/dashboard/projects"),
                "https://cdn.modtale.test/icon.png"
        );
        verify(adminAuditLogger).logAction(
                "admin-1",
                "REJECT_VERSION",
                "project-1",
                "VERSION",
                "VerID: version-1, Reason: Missing metadata"
        );
    }

    private static Project project(String id, String authorId) {
        Project project = new Project();
        project.setId(id);
        project.setAuthorId(authorId);
        project.setAuthor("Ada");
        project.setTitle("Sky Tools");
        project.setStatus(ProjectStatus.PENDING);
        project.setImageUrl("https://cdn.modtale.test/icon.png");
        project.setVersions(new ArrayList<>());
        return project;
    }

    private static ProjectVersion version(String id, String versionNumber) {
        ProjectVersion version = new ProjectVersion();
        version.setId(id);
        version.setVersionNumber(versionNumber);
        return version;
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
