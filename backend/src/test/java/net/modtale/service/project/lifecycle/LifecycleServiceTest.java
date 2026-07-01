package net.modtale.service.project.lifecycle;

import java.util.ArrayList;
import java.util.List;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.communication.ProjectNotificationService;
import net.modtale.service.communication.WebhookService;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.access.ProjectMutationGuard;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.project.validation.ValidationService;
import net.modtale.service.project.version.VersionArtifactService;
import net.modtale.service.project.version.VersionDependencyService;
import net.modtale.service.project.version.VersionMutationOrchestrationService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import net.modtale.service.security.scan.ScanService;
import net.modtale.service.security.validation.SanitizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LifecycleServiceTest {

    private LifecycleService lifecycleService;
    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private ValidationService validationService;
    private ProjectNotificationService projectNotificationService;
    private WebhookService webhookService;
    private TrackingService trackingService;
    private SanitizationService sanitizationService;
    private UserRepository userRepository;
    private AccessControlService accessControlService;
    private ProjectAccessService projectAccessService;
    private ProjectMutationGuard projectMutationGuard;
    private SecurityIssueAnalysisService securityIssueAnalysisService;
    private VersionMutationOrchestrationService versionMutationOrchestrationService;
    private ScanService scanService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        validationService = mock(ValidationService.class);
        projectNotificationService = mock(ProjectNotificationService.class);
        webhookService = mock(WebhookService.class);
        trackingService = mock(TrackingService.class);
        sanitizationService = mock(SanitizationService.class);
        userRepository = mock(UserRepository.class);
        accessControlService = mock(AccessControlService.class);
        projectAccessService = new ProjectAccessService(projectService, accessControlService);
        projectMutationGuard = new ProjectMutationGuard();
        securityIssueAnalysisService = mock(SecurityIssueAnalysisService.class);
        scanService = mock(ScanService.class);
        versionMutationOrchestrationService = new VersionMutationOrchestrationService(
                validationService,
                scanService,
                sanitizationService,
                mock(VersionArtifactService.class),
                mock(VersionDependencyService.class),
                mock(ProjectDeletionService.class)
        );
        ProjectDraftWorkflowService projectDraftWorkflowService = new ProjectDraftWorkflowService(
                projectRepository,
                projectService,
                validationService,
                webhookService,
                sanitizationService,
                userRepository,
                projectAccessService,
                projectMutationGuard,
                versionMutationOrchestrationService,
                new AppLimitProperties(10, 5, 10, 5, 5, 5, 20, 10)
        );
        ProjectPublicationService projectPublicationService = new ProjectPublicationService(
                projectRepository,
                projectService,
                projectNotificationService,
                webhookService,
                trackingService,
                mock(ScoringService.class),
                accessControlService,
                projectAccessService,
                securityIssueAnalysisService
        );
        lifecycleService = new LifecycleService(
                projectDraftWorkflowService,
                projectPublicationService
        );
    }

    @Test
    void createDraftUsesOrganizationOwnerAndSanitizesSavedFields() {
        User creator = user("user-1", "ItsNeil17", User.AccountType.USER, true);
        User organization = user("org-1", "ItsNeil17", User.AccountType.ORGANIZATION, true);
        organization.setOrganizationMembers(List.of(new User.OrganizationMember("user-1", "owner-role")));

        when(projectRepository.existsByTitleIgnoreCase("Raw Title")).thenReturn(false);
        when(projectRepository.countByAuthorId("org-1")).thenReturn(0L);
        when(projectRepository.existsBySlug("Lock-In")).thenReturn(false);
        when(userRepository.findById("org-1")).thenReturn(java.util.Optional.of(organization));
        when(sanitizationService.sanitizePlainText("Raw Title")).thenReturn("Clean Title");
        when(sanitizationService.sanitizePlainText("Raw Description")).thenReturn("Clean Description");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Project draft = lifecycleService.createDraft(
                "Raw Title",
                "Raw Description",
                ProjectClassification.MODPACK,
                creator,
                "org-1",
                "Lock-In"
        );

        assertEquals(ProjectStatus.DRAFT, draft.getStatus());
        assertEquals("org-1", draft.getAuthorId());
        assertEquals("ItsNeil17", draft.getAuthor());
        assertEquals("Clean Title", draft.getTitle());
        assertEquals("Clean Description", draft.getDescription());
        assertEquals("lock-in", draft.getSlug());
        assertTrue(draft.isAllowComments());
        assertTrue(draft.isAllowModpacks());
        assertEquals(2, draft.getProjectRoles().size());
        assertEquals(List.of(), draft.getTeamMembers());
        assertEquals(List.of(), draft.getTeamInvites());

        verify(validationService).validateSlug("Lock-In");
    }

    @Test
    void submitProjectMarksVersionsPendingAndTriggersAdminWebhookWhenNoScanIsRunning() {
        User user = user("user-1", "ItsNeil17", User.AccountType.USER, true);
        Project project = editableProject("project-1", ProjectClassification.DATA, ProjectStatus.DRAFT);
        project.setDescription("Detailed description");
        project.setTags(new ArrayList<>(List.of("magic")));
        project.setRepositoryUrl("https://github.com/modtale/sky-tools");
        project.setLicense("MIT");
        project.setExpiresAt("soon");

        ProjectVersion nullReview = version("1.0.0");
        nullReview.setReviewStatus(null);
        ProjectVersion rejectedReview = version("1.1.0");
        rejectedReview.setReviewStatus(ProjectVersion.ReviewStatus.REJECTED);
        ProjectVersion approvedReview = version("1.2.0");
        approvedReview.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        project.setVersions(new ArrayList<>(List.of(nullReview, rejectedReview, approvedReview)));

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_STATUS_SUBMIT")).thenReturn(true);

        lifecycleService.submitProject("project-1", user);

        assertEquals(ProjectStatus.PENDING, project.getStatus());
        assertNull(project.getExpiresAt());
        assertEquals(ProjectVersion.ReviewStatus.PENDING, nullReview.getReviewStatus());
        assertEquals(ProjectVersion.ReviewStatus.PENDING, rejectedReview.getReviewStatus());
        assertEquals(ProjectVersion.ReviewStatus.APPROVED, approvedReview.getReviewStatus());

        verify(validationService).validateSlug("sky-tools");
        verify(validationService).validateRepositoryUrl("https://github.com/modtale/sky-tools");
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        verify(webhookService).triggerAdminNewProjectWebhook(project);
    }

    @Test
    void submitProjectQueuesInitialScanForDraftArtifactsBeforeReview() {
        User user = user("user-1", "ItsNeil17", User.AccountType.USER, true);
        Project project = editableProject("project-1", ProjectClassification.DATA, ProjectStatus.DRAFT);
        project.setDescription("Detailed description");
        project.setTags(new ArrayList<>(List.of("magic")));
        project.setRepositoryUrl("https://github.com/modtale/sky-tools");
        project.setLicense("MIT");

        ProjectVersion uploadedDraftVersion = version("1.0.0");
        uploadedDraftVersion.setFileUrl("/files/data/bundle.zip");
        uploadedDraftVersion.setScanResult(null);
        project.setVersions(new ArrayList<>(List.of(uploadedDraftVersion)));

        ScanResult queuedScan = new ScanResult(ScanStatus.SCANNING, 0, List.of());

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_STATUS_SUBMIT")).thenReturn(true);
        when(scanService.createQueuedScanResult(1, "Initial scan queued.")).thenReturn(queuedScan);

        lifecycleService.submitProject("project-1", user);

        assertEquals(ProjectStatus.PENDING, project.getStatus());
        assertEquals(queuedScan, uploadedDraftVersion.getScanResult());
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        verify(scanService).enqueueBackgroundScan(
                "project-1",
                uploadedDraftVersion.getId(),
                "/files/data/bundle.zip",
                "/files/data/bundle.zip",
                false,
                1
        );
        verify(webhookService, never()).triggerAdminNewProjectWebhook(project);
    }

    @Test
    void submitProjectSkipsAdminWebhookWhileAnyVersionIsStillScanning() {
        User user = user("user-1", "ItsNeil17", User.AccountType.USER, true);
        Project project = editableProject("project-1", ProjectClassification.DATA, ProjectStatus.DRAFT);
        project.setDescription("Detailed description");
        project.setTags(new ArrayList<>(List.of("magic")));
        project.setRepositoryUrl("https://github.com/modtale/sky-tools");
        project.setLicense("MIT");

        ProjectVersion scanningVersion = version("1.0.0");
        scanningVersion.setScanResult(new ScanResult(ScanStatus.SCANNING, 0, List.of()));
        project.setVersions(new ArrayList<>(List.of(scanningVersion)));

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.hasProjectPermission(project, user, "PROJECT_STATUS_SUBMIT")).thenReturn(true);

        lifecycleService.submitProject("project-1", user);

        verify(projectRepository).save(project);
        verify(webhookService, never()).triggerAdminNewProjectWebhook(project);
    }

    @Test
    void publishProjectApprovesVersionsAndBroadcastsFirstPublication() {
        User admin = user("user-1", "ItsNeil17", User.AccountType.USER, true);
        Project project = editableProject("project-1", ProjectClassification.DATA, ProjectStatus.PENDING);
        project.setCreatedAt(null);
        project.setImageUrl("");

        ProjectVersion scheduled = version("1.0.0");
        scheduled.setReviewStatus(ProjectVersion.ReviewStatus.SCHEDULED);
        scheduled.setScheduledPublishDate("2026-06-09T12:00:00");
        ProjectVersion approved = version("1.1.0");
        approved.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        project.setVersions(new ArrayList<>(List.of(scheduled, approved)));

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.canApproveProjectReviews(admin)).thenReturn(true);
        when(projectRepository.save(project)).thenReturn(project);

        lifecycleService.publishProject("project-1", admin);

        assertEquals(ProjectStatus.PUBLISHED, project.getStatus());
        assertNotNull(project.getCreatedAt());
        assertNotNull(project.getUpdatedAt());
        assertEquals("ItsNeil17", project.getApprovedBy());
        assertEquals("https://modtale.net/assets/favicon.svg", project.getImageUrl());
        assertEquals(ProjectVersion.ReviewStatus.APPROVED, scheduled.getReviewStatus());
        assertNull(scheduled.getScheduledPublishDate());
        assertEquals(ProjectVersion.ReviewStatus.APPROVED, approved.getReviewStatus());

        verify(securityIssueAnalysisService).pruneApprovedScanResults(project);
        verify(projectService).evictProjectCache(project);
        verify(projectNotificationService).notifyNewProject(project);
        verify(webhookService).triggerWebhook(project);
        verify(webhookService).triggerDiscordWebhook(project);
        verify(trackingService).logNewProject("project-1");
    }

    @Test
    void publishProjectRestorationUsesProjectPermissionWithoutBroadcastingNewProjectEvents() {
        User maintainer = user("user-2", "ItsNeil17", User.AccountType.USER, true);
        Project project = editableProject("project-1", ProjectClassification.DATA, ProjectStatus.ARCHIVED);
        project.setCreatedAt("2025-01-01T00:00:00");
        project.setImageUrl("https://cdn.modtale.net/icon.png");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.isAdmin(maintainer)).thenReturn(false);
        when(accessControlService.hasProjectPermission(project, maintainer, "PROJECT_STATUS_PUBLISH")).thenReturn(true);
        when(projectRepository.save(project)).thenReturn(project);

        lifecycleService.publishProject("project-1", maintainer);

        assertEquals(ProjectStatus.PUBLISHED, project.getStatus());
        assertEquals("2025-01-01T00:00:00", project.getCreatedAt());
        assertNull(project.getApprovedBy());

        verify(accessControlService).hasProjectPermission(project, maintainer, "PROJECT_STATUS_PUBLISH");
        verify(projectService).evictProjectCache(project);
        verify(projectNotificationService, never()).notifyNewProject(any(Project.class));
        verify(webhookService, never()).triggerWebhook(any(Project.class));
        verify(webhookService, never()).triggerDiscordWebhook(any(Project.class));
        verify(trackingService, never()).logNewProject(anyString());
    }

    private static Project editableProject(String id, ProjectClassification classification, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setSlug("sky-tools");
        project.setTitle("LevelingCore");
        project.setClassification(classification);
        project.setAuthorId("author-1");
        project.setAuthor("ItsNeil17");
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

    private static User user(String id, String username, User.AccountType accountType, boolean emailVerified) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setAccountType(accountType);
        user.setEmailVerified(emailVerified);
        return user;
    }
}
