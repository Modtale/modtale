package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.communication.WebhookService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.security.SanitizationService;
import net.modtale.service.security.SecurityIssueAnalysisService;
import net.modtale.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private NotificationService notificationService;
    private WebhookService webhookService;
    private TrackingService trackingService;
    private SanitizationService sanitizationService;
    private MongoTemplate mongoTemplate;
    private StorageService storageService;
    private UserRepository userRepository;
    private AccessControlService accessControlService;
    private SecurityIssueAnalysisService securityIssueAnalysisService;

    @BeforeEach
    void setUp() {
        lifecycleService = new LifecycleService();
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        validationService = mock(ValidationService.class);
        notificationService = mock(NotificationService.class);
        webhookService = mock(WebhookService.class);
        trackingService = mock(TrackingService.class);
        sanitizationService = mock(SanitizationService.class);
        mongoTemplate = mock(MongoTemplate.class);
        storageService = mock(StorageService.class);
        userRepository = mock(UserRepository.class);
        accessControlService = mock(AccessControlService.class);
        securityIssueAnalysisService = mock(SecurityIssueAnalysisService.class);

        ReflectionTestUtils.setField(lifecycleService, "projectRepository", projectRepository);
        ReflectionTestUtils.setField(lifecycleService, "projectService", projectService);
        ReflectionTestUtils.setField(lifecycleService, "validationService", validationService);
        ReflectionTestUtils.setField(lifecycleService, "notificationService", notificationService);
        ReflectionTestUtils.setField(lifecycleService, "webhookService", webhookService);
        ReflectionTestUtils.setField(lifecycleService, "trackingService", trackingService);
        ReflectionTestUtils.setField(lifecycleService, "sanitizer", sanitizationService);
        ReflectionTestUtils.setField(lifecycleService, "mongoTemplate", mongoTemplate);
        ReflectionTestUtils.setField(lifecycleService, "storageService", storageService);
        ReflectionTestUtils.setField(lifecycleService, "userRepository", userRepository);
        ReflectionTestUtils.setField(lifecycleService, "accessControlService", accessControlService);
        ReflectionTestUtils.setField(lifecycleService, "securityIssueAnalysisService", securityIssueAnalysisService);
        ReflectionTestUtils.setField(lifecycleService, "maxProjectsPerUser", 5);
    }

    @Test
    void createDraftUsesOrganizationOwnerAndSanitizesSavedFields() {
        User creator = user("user-1", "Ada", User.AccountType.USER, true);
        User organization = user("org-1", "SkyOrg", User.AccountType.ORGANIZATION, true);
        organization.setOrganizationMembers(List.of(new User.OrganizationMember("user-1", "owner-role")));

        when(projectRepository.existsByTitleIgnoreCase("Raw Title")).thenReturn(false);
        when(projectRepository.countByAuthorId("org-1")).thenReturn(0L);
        when(projectRepository.existsBySlug("Sky-Ship")).thenReturn(false);
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
                "Sky-Ship"
        );

        assertEquals(ProjectStatus.DRAFT, draft.getStatus());
        assertEquals("org-1", draft.getAuthorId());
        assertEquals("SkyOrg", draft.getAuthor());
        assertEquals("Clean Title", draft.getTitle());
        assertEquals("Clean Description", draft.getDescription());
        assertEquals("sky-ship", draft.getSlug());
        assertTrue(draft.isAllowComments());
        assertTrue(draft.isAllowModpacks());
        assertEquals(2, draft.getProjectRoles().size());
        assertEquals(List.of(), draft.getTeamMembers());
        assertEquals(List.of(), draft.getTeamInvites());

        verify(validationService).validateSlug("Sky-Ship");
    }

    @Test
    void submitProjectMarksVersionsPendingAndTriggersAdminWebhookWhenNoScanIsRunning() {
        User user = user("user-1", "Ada", User.AccountType.USER, true);
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
    void submitProjectSkipsAdminWebhookWhileAnyVersionIsStillScanning() {
        User user = user("user-1", "Ada", User.AccountType.USER, true);
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
        User admin = user("user-1", "Ada", User.AccountType.USER, true);
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
        when(accessControlService.isAdmin(admin)).thenReturn(true);
        when(projectRepository.save(project)).thenReturn(project);

        lifecycleService.publishProject("project-1", admin);

        assertEquals(ProjectStatus.PUBLISHED, project.getStatus());
        assertNotNull(project.getCreatedAt());
        assertNotNull(project.getUpdatedAt());
        assertEquals("Ada", project.getApprovedBy());
        assertEquals("https://modtale.net/assets/favicon.svg", project.getImageUrl());
        assertEquals(ProjectVersion.ReviewStatus.APPROVED, scheduled.getReviewStatus());
        assertNull(scheduled.getScheduledPublishDate());
        assertEquals(ProjectVersion.ReviewStatus.APPROVED, approved.getReviewStatus());

        verify(securityIssueAnalysisService).markIssuesAcceptedForApprovedVersion(scheduled);
        verify(securityIssueAnalysisService).markIssuesAcceptedForApprovedVersion(approved);
        verify(projectService).evictProjectCache(project);
        verify(notificationService).notifyNewProject(project);
        verify(webhookService).triggerWebhook(project);
        verify(webhookService).triggerDiscordWebhook(project);
        verify(trackingService).logNewProject("project-1");
    }

    @Test
    void publishProjectRestorationUsesProjectPermissionWithoutBroadcastingNewProjectEvents() {
        User maintainer = user("user-2", "Bea", User.AccountType.USER, true);
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
        verify(notificationService, never()).notifyNewProject(any(Project.class));
        verify(webhookService, never()).triggerWebhook(any(Project.class));
        verify(webhookService, never()).triggerDiscordWebhook(any(Project.class));
        verify(trackingService, never()).logNewProject(anyString());
    }

    @Test
    void performHardDeleteScrubsProjectsThatAreStillNeededForDependencies() {
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
        project.setProjectRoles(new ArrayList<>(List.of(new Project.ProjectRole("role-1", "Admin", "#fff", List.of("PROJECT_EDIT_METADATA")))));
        project.setComments(new ArrayList<>());
        project.setTags(new ArrayList<>(List.of("magic")));
        project.setDeletedAt(LocalDateTime.now());

        when(projectRepository.findByDependency("project-1")).thenReturn(List.of(editableProject("dependent-1", ProjectClassification.DATA, ProjectStatus.PUBLISHED)));
        when(projectRepository.save(project)).thenReturn(project);

        lifecycleService.performHardDelete(project);

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
    void performDeletionStrategyMarksPublishedProjectsDeletedAndTracksRemoval() {
        Project project = editableProject("project-1", ProjectClassification.DATA, ProjectStatus.PUBLISHED);

        lifecycleService.performDeletionStrategy(project);

        assertEquals(ProjectStatus.DELETED, project.getStatus());
        assertNotNull(project.getDeletedAt());
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        verify(trackingService).logDeletedProject("project-1");
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

    private static User user(String id, String username, User.AccountType accountType, boolean emailVerified) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setAccountType(accountType);
        user.setEmailVerified(emailVerified);
        return user;
    }
}
