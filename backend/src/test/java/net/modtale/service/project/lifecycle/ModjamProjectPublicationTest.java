package net.modtale.service.project.lifecycle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.model.jam.Modjam;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.jam.ModjamRepository;
import net.modtale.repository.jam.ModjamSubmissionRepository;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.communication.ProjectNotificationService;
import net.modtale.service.communication.WebhookService;
import net.modtale.service.jam.ModjamEmbargoService;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ModjamProjectPublicationTest {
    private ProjectPublicationService service;
    private ProjectRepository projects;
    private ProjectService projectService;
    private ProjectNotificationService notifications;
    private WebhookService webhooks;
    private TrackingService tracking;
    private ScoringService scoring;
    private ProjectAccessService projectAccess;
    private ModjamRepository jams;
    private Project project;
    private Modjam jam;
    private User admin;
    private net.modtale.service.jam.ModjamProjectReleasePersistence releasePersistence;

    @BeforeEach
    void setUp() {
        projects = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        notifications = mock(ProjectNotificationService.class);
        webhooks = mock(WebhookService.class);
        tracking = mock(TrackingService.class);
        scoring = mock(ScoringService.class);
        projectAccess = mock(ProjectAccessService.class);
        jams = mock(ModjamRepository.class);
        AccessControlService access = mock(AccessControlService.class);
        var embargo = new ModjamEmbargoService(jams, mock(ModjamSubmissionRepository.class));
        releasePersistence = mock(net.modtale.service.jam.ModjamProjectReleasePersistence.class);
        service = new ProjectPublicationService(projects, projectService, notifications, webhooks,
                tracking, scoring, access, projectAccess, mock(SecurityIssueAnalysisService.class), embargo, releasePersistence);
        jam = new Modjam();
        jam.setId("jam-1");
        jam.setStatus("ACTIVE");
        jam.setHideSubmissions(true);
        jam.setStartDate(Instant.now().minusSeconds(3600));
        jam.setEndDate(Instant.now().plusSeconds(3600));
        jam.setVotingEndDate(Instant.now().plusSeconds(7200));
        project = new Project();
        project.setId("project-1");
        project.setStatus(ProjectStatus.PENDING);
        project.setClassification(ProjectClassification.PLUGIN);
        project.setModjamIds(new ArrayList<>(List.of("jam-1")));
        ProjectVersion version = new ProjectVersion();
        version.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        project.setVersions(new ArrayList<>(List.of(version)));
        admin = new User();
        admin.setUsername("reviewer");
        when(access.canApproveProjectReviews(admin)).thenReturn(true);
        when(access.hasProjectPermission(any(), any(), anyString())).thenReturn(true);
        when(jams.findById("jam-1")).thenReturn(Optional.of(jam));
        when(projectAccess.requireProject("project-1")).thenReturn(project);
        when(projectAccess.requireProjectPermission(anyString(), any(), anyString(), anyString())).thenReturn(project);
        when(projects.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(releasePersistence.claimRelease(any())).thenAnswer(invocation -> {
            Project claimed = invocation.getArgument(0);
            claimed.setStatus(ProjectStatus.PUBLISHED);
            claimed.setModjamPublicationPending(false);
            return claimed;
        });
    }

    @Test
    void approvalKeepsSecretEntryPrivateAndSuppressesPublicSideEffects() {
        service.publishProject("project-1", admin);
        assertEquals(ProjectStatus.PRIVATE, project.getStatus());
        assertTrue(project.isModjamPublicationPending());
        assertEquals(ProjectVersion.ReviewStatus.APPROVED, project.getVersions().getFirst().getReviewStatus());
        verify(projectService).evictProjectCache(project);
        verifyNoInteractions(notifications, webhooks, tracking);
    }

    @Test
    void staleVotingStatusCannotReleaseEntryBeforeSubmissionDeadline() {
        jam.setStatus("VOTING");
        service.publishProject("project-1", admin);
        clearInvocations(projects, projectService);
        service.releaseModjamEmbargo(project);
        assertEquals(ProjectStatus.PRIVATE, project.getStatus());
        verifyNoInteractions(projects, projectService, notifications, webhooks, tracking);
    }

    @Test
    void secretEntryPublishesOnceVotingOpensAndInvalidatesCaches() {
        service.publishProject("project-1", admin);
        clearInvocations(projects, projectService, scoring);
        jam.setEndDate(Instant.now().minusSeconds(1));
        service.releaseModjamEmbargo(project);
        assertEquals(ProjectStatus.PUBLISHED, project.getStatus());
        assertFalse(project.isModjamPublicationPending());
        verify(releasePersistence).claimRelease(project);
        verifyNoInteractions(scoring);
        verify(projectService).evictProjectCache(project);
        verify(notifications).notifyNewProject(project);
        verify(webhooks).triggerWebhook(project);
        verify(webhooks).triggerDiscordWebhook(project);
        verify(tracking).logNewProject("project-1");
        clearInvocations(projects, projectService, notifications, webhooks, tracking);
        service.releaseModjamEmbargo(project);
        verifyNoInteractions(projects, projectService, notifications, webhooks, tracking);
    }

    @Test
    void approvalAfterVotingStartsPublishesNormally() {
        jam.setEndDate(Instant.now().minusSeconds(1));
        service.publishProject("project-1", admin);
        assertEquals(ProjectStatus.PUBLISHED, project.getStatus());
        assertFalse(project.isModjamPublicationPending());
        verify(notifications).notifyNewProject(project);
    }

    @Test
    void anotherSecretJamContinuesToHoldPublication() {
        service.publishProject("project-1", admin);
        jam.setEndDate(Instant.now().minusSeconds(1));
        Modjam other = new Modjam();
        other.setStatus("DRAFT");
        other.setHideSubmissions(true);
        project.getModjamIds().add("other-jam");
        when(jams.findById("other-jam")).thenReturn(Optional.of(other));
        service.releaseModjamEmbargo(project);
        assertEquals(ProjectStatus.PRIVATE, project.getStatus());
        verifyNoInteractions(notifications, webhooks, tracking);
    }

    @Test
    void unrelatedPrivateProjectIsNeverReleased() {
        project.setStatus(ProjectStatus.PRIVATE);
        project.setModjamPublicationPending(false);
        jam.setEndDate(Instant.now().minusSeconds(1));
        service.releaseModjamEmbargo(project);
        assertEquals(ProjectStatus.PRIVATE, project.getStatus());
        verifyNoInteractions(projects, notifications, webhooks, tracking);
    }

    @Test
    void ownerCannotUnlistOrArchiveSecretEntryDuringEmbargo() {
        service.publishProject("project-1", admin);
        clearInvocations(projects);
        assertThrows(InvalidProjectRequestException.class, () -> service.unlistProject("project-1", admin));
        assertThrows(InvalidProjectRequestException.class, () -> service.archiveProject("project-1", admin));
        assertEquals(ProjectStatus.PRIVATE, project.getStatus());
        verifyNoInteractions(projects, notifications, webhooks, tracking);
    }

    @Test
    void explicitPrivateChoiceCancelsAutomaticPublication() {
        service.publishProject("project-1", admin);
        service.privateProject("project-1", admin);
        assertFalse(project.isModjamPublicationPending());
        jam.setEndDate(Instant.now().minusSeconds(1));
        service.releaseModjamEmbargo(project);
        assertEquals(ProjectStatus.PRIVATE, project.getStatus());
        verifyNoInteractions(notifications, webhooks, tracking);
    }

    @Test
    void staleReleaseSnapshotCannotOverwriteAConcurrentPrivateChoice() {
        service.publishProject("project-1", admin);
        Project stale = new Project();
        org.springframework.beans.BeanUtils.copyProperties(project, stale);
        service.privateProject("project-1", admin);
        jam.setEndDate(Instant.now().minusSeconds(1));
        doReturn(null).when(releasePersistence).claimRelease(stale);
        clearInvocations(projects, projectService, scoring);
        service.releaseModjamEmbargo(stale);
        assertEquals(ProjectStatus.PRIVATE, project.getStatus());
        assertFalse(project.isModjamPublicationPending());
        verifyNoInteractions(projects, projectService, scoring, notifications, webhooks, tracking);
    }

    @Test
    void competingSchedulerClaimsOnlyNotifyOnce() {
        service.publishProject("project-1", admin);
        Project stale = new Project();
        org.springframework.beans.BeanUtils.copyProperties(project, stale);
        Project claimed = new Project();
        org.springframework.beans.BeanUtils.copyProperties(project, claimed);
        claimed.setStatus(ProjectStatus.PUBLISHED);
        claimed.setModjamPublicationPending(false);
        jam.setEndDate(Instant.now().minusSeconds(1));
        doReturn(claimed).doReturn(null).when(releasePersistence).claimRelease(stale);
        clearInvocations(projects, projectService, notifications, webhooks, tracking);
        service.releaseModjamEmbargo(stale);
        service.releaseModjamEmbargo(stale);
        verify(releasePersistence, times(2)).claimRelease(stale);
        verify(projectService).evictProjectCache(claimed);
        verify(notifications).notifyNewProject(claimed);
        verify(webhooks).triggerWebhook(claimed);
        verify(webhooks).triggerDiscordWebhook(claimed);
        verify(tracking).logNewProject("project-1");
        verifyNoInteractions(projects);
    }
}
