package net.modtale.service.project.lifecycle;

import java.time.LocalDateTime;
import net.modtale.service.admin.review.ProjectReviewPersistence;
import net.modtale.service.admin.review.ProjectReviewSnapshot;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ProjectOperationForbiddenException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.communication.ProjectNotificationService;
import net.modtale.service.communication.WebhookService;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import org.springframework.stereotype.Service;

@Service
public class ProjectPublicationService {

    private final ProjectReviewPersistence reviewPersistence;
    private final ProjectService projectService;
    private final ProjectNotificationService projectNotificationService;
    private final WebhookService webhookService;
    private final TrackingService trackingService;
    private final ScoringService scoringService;
    private final AccessControlService accessControlService;
    private final ProjectAccessService projectAccessService;
    private final SecurityIssueAnalysisService securityIssueAnalysisService;

    public ProjectPublicationService(
            ProjectService projectService,
            ProjectNotificationService projectNotificationService,
            WebhookService webhookService,
            TrackingService trackingService,
            ScoringService scoringService,
            AccessControlService accessControlService,
            ProjectAccessService projectAccessService,
            SecurityIssueAnalysisService securityIssueAnalysisService,
            ProjectReviewPersistence reviewPersistence
    ) {
        this.reviewPersistence = reviewPersistence;
        this.projectService = projectService;
        this.projectNotificationService = projectNotificationService;
        this.webhookService = webhookService;
        this.trackingService = trackingService;
        this.scoringService = scoringService;
        this.accessControlService = accessControlService;
        this.projectAccessService = projectAccessService;
        this.securityIssueAnalysisService = securityIssueAnalysisService;
    }

    public void revertProjectToDraft(String id, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_STATUS_REVERT",
                "You do not have permission to revert this project.");
        var snapshot = reviewPersistence.capture(id, ProjectReviewSnapshot.token(project));
        project = snapshot.project();
        if (project.getStatus() != ProjectStatus.PENDING) {
            throw new InvalidProjectRequestException(
                    "Only projects that are pending review can be reverted to draft.");
        }
        project.setStatus(ProjectStatus.DRAFT);
        scoringService.markProjectRankingDirty(project);
        project.setUpdatedAt(LocalDateTime.now().toString());
        if (!reviewPersistence.apply(snapshot, null)) throw ProjectReviewSnapshot.conflict();
        projectService.evictProjectCache(project);
    }

    public void archiveProject(String id, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_STATUS_ARCHIVE",
                "You do not have permission to archive this project.");
        var snapshot = reviewPersistence.capture(id, ProjectReviewSnapshot.token(project));
        project = snapshot.project();
        if (project.getStatus() != ProjectStatus.PUBLISHED
                && project.getStatus() != ProjectStatus.UNLISTED
                && project.getStatus() != ProjectStatus.PRIVATE) {
            throw new InvalidProjectRequestException("Only published, unlisted, or private projects can be archived.");
        }
        project.setStatus(ProjectStatus.ARCHIVED);
        project.setExpiresAt(null);
        scoringService.markProjectRankingDirty(project);
        project.setUpdatedAt(LocalDateTime.now().toString());
        if (!reviewPersistence.apply(snapshot, null)) throw ProjectReviewSnapshot.conflict();
        projectService.evictProjectCache(project);
    }

    public void unlistProject(String id, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_STATUS_UNLIST",
                "You do not have permission to unlist this project.");
        var snapshot = reviewPersistence.capture(id, ProjectReviewSnapshot.token(project));
        project = snapshot.project();
        if (project.getStatus() != ProjectStatus.PUBLISHED && project.getStatus() != ProjectStatus.ARCHIVED) {
            throw new InvalidProjectRequestException("Only published or archived projects can be unlisted.");
        }
        project.setStatus(ProjectStatus.UNLISTED);
        project.setExpiresAt(null);
        scoringService.markProjectRankingDirty(project);
        project.setUpdatedAt(LocalDateTime.now().toString());
        if (!reviewPersistence.apply(snapshot, null)) throw ProjectReviewSnapshot.conflict();
        projectService.evictProjectCache(project);
    }

    public void privateProject(String id, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_STATUS_UNLIST",
                "You do not have permission to make this project private.");
        var snapshot = reviewPersistence.capture(id, ProjectReviewSnapshot.token(project));
        project = snapshot.project();
        if (project.getStatus() == ProjectStatus.PENDING || project.getStatus() == ProjectStatus.DELETED) {
            throw new InvalidProjectRequestException("Pending or deleted projects cannot be made private.");
        }
        project.setStatus(ProjectStatus.PRIVATE);
        project.setExpiresAt(null);
        scoringService.markProjectRankingDirty(project);
        project.setUpdatedAt(LocalDateTime.now().toString());
        if (!reviewPersistence.apply(snapshot, null)) throw ProjectReviewSnapshot.conflict();
        projectService.evictProjectCache(project);
    }

    public void publishProject(String id, User user) {
        publishProject(id, user, null, null);
    }

    public void publishProject(String id, User user, String reviewToken, String reviewedVersionId) {
        var snapshot = reviewToken == null ? null : reviewPersistence.capture(id, reviewToken);
        boolean reviewed = snapshot != null;
        if (snapshot == null) {
            Project current = projectAccessService.requireProject(id);
            snapshot = reviewPersistence.capture(id, ProjectReviewSnapshot.token(current));
        }
        Project project = snapshot.project();
        boolean canApproveReviews = accessControlService.canApproveProjectReviews(user);
        boolean isRestoration = project.getStatus() == ProjectStatus.ARCHIVED
                || project.getStatus() == ProjectStatus.UNLISTED
                || project.getStatus() == ProjectStatus.PRIVATE;
        boolean isNew = project.getStatus() == ProjectStatus.PENDING || project.getCreatedAt() == null;

        if (isRestoration && !isNew) {
            if (!accessControlService.hasProjectPermission(project, user, "PROJECT_STATUS_PUBLISH")) {
                throw new ProjectOperationForbiddenException("You do not have permission to republish this project.");
            }
        } else if (!canApproveReviews) {
            throw new ProjectOperationForbiddenException("Only administrators with review approval permission can publish a new project.");
        }
        if (!isRestoration && !reviewed) {
            throw ProjectReviewSnapshot.conflict();
        }
        if (reviewed && project.getStatus() != ProjectStatus.PENDING) {
            throw new InvalidProjectRequestException("Only pending projects can be approved through review.");
        }
        ProjectVersion selected = null;
        if (reviewed && reviewedVersionId != null && project.getVersions() != null) {
            var matches = project.getVersions().stream().filter(version -> reviewedVersionId.equals(version.getId())).toList();
            if (matches.size() != 1) throw ProjectReviewSnapshot.conflict();
            selected = matches.getFirst();
            if (selected.getReviewStatus() != ProjectVersion.ReviewStatus.PENDING
                    && selected.getReviewStatus() != ProjectVersion.ReviewStatus.SCHEDULED
                    && selected.getReviewStatus() != ProjectVersion.ReviewStatus.APPROVED) {
                throw new InvalidProjectRequestException("This version is not available for approval.");
            }
        }
        if (reviewed && selected == null && project.getVersions() != null && !project.getVersions().isEmpty()) {
            throw new InvalidProjectRequestException("Select the inspected version before publishing this project.");
        }
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setExpiresAt(null);
        project.setUpdatedAt(LocalDateTime.now().toString());
        scoringService.markProjectRankingDirty(project);

        if (selected != null && selected.getReviewStatus() != ProjectVersion.ReviewStatus.APPROVED) {
            selected.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
            selected.setScheduledPublishDate(null);
            selected.setRejectionReason(null);
            securityIssueAnalysisService.markIssuesAcceptedForApprovedVersion(selected);
        }

        if (isNew) {
            project.setCreatedAt(LocalDateTime.now().toString());
        }
        if (!isRestoration && canApproveReviews && user != null) {
            project.setApprovedBy(user.getUsername());
        }
        if (project.getImageUrl() == null || project.getImageUrl().isEmpty()) {
            project.setImageUrl("https://modtale.net/assets/favicon.svg");
        }

        if (!reviewPersistence.apply(snapshot, reviewedVersionId)) throw ProjectReviewSnapshot.conflict();
        Project saved = project;
        projectService.evictProjectCache(saved);

        if (isNew) {
            projectNotificationService.notifyNewProject(saved);
            webhookService.triggerWebhook(saved);
            webhookService.triggerDiscordWebhook(saved);
            trackingService.logNewProject(saved.getId());
        }
    }

    public void updateProjectStatus(String id, ProjectStatus status, User user, String permissionRequired) {
        Project project = projectAccessService.requireProjectPermission(id, user, permissionRequired,
                "You do not have permission to update this project.");
        var snapshot = reviewPersistence.capture(id, ProjectReviewSnapshot.token(project));
        project = snapshot.project();
        project.setStatus(status);
        project.setExpiresAt(null);
        scoringService.markProjectRankingDirty(project);
        project.setUpdatedAt(LocalDateTime.now().toString());
        if (!reviewPersistence.apply(snapshot, null)) throw ProjectReviewSnapshot.conflict();
        projectService.evictProjectCache(project);
    }
}
