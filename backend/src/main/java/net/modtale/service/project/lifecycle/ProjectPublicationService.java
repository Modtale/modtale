package net.modtale.service.project.lifecycle;

import java.time.LocalDateTime;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ProjectOperationForbiddenException;
import net.modtale.exception.VersionStateConflictException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.communication.ProjectNotificationService;
import net.modtale.service.communication.WebhookService;
import net.modtale.service.jam.ModjamEmbargoService;
import net.modtale.service.jam.ModjamProjectReleasePersistence;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import org.springframework.stereotype.Service;

@Service
public class ProjectPublicationService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ProjectNotificationService projectNotificationService;
    private final WebhookService webhookService;
    private final TrackingService trackingService;
    private final ScoringService scoringService;
    private final AccessControlService accessControlService;
    private final ProjectAccessService projectAccessService;
    private final SecurityIssueAnalysisService securityIssueAnalysisService;
    private final ModjamEmbargoService modjamEmbargoService;
    private final ModjamProjectReleasePersistence modjamProjectReleasePersistence;

    public ProjectPublicationService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ProjectNotificationService projectNotificationService,
            WebhookService webhookService,
            TrackingService trackingService,
            ScoringService scoringService,
            AccessControlService accessControlService,
            ProjectAccessService projectAccessService,
            SecurityIssueAnalysisService securityIssueAnalysisService,
            ModjamEmbargoService modjamEmbargoService,
            ModjamProjectReleasePersistence modjamProjectReleasePersistence
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.projectNotificationService = projectNotificationService;
        this.webhookService = webhookService;
        this.trackingService = trackingService;
        this.scoringService = scoringService;
        this.accessControlService = accessControlService;
        this.projectAccessService = projectAccessService;
        this.securityIssueAnalysisService = securityIssueAnalysisService;
        this.modjamEmbargoService = modjamEmbargoService;
        this.modjamProjectReleasePersistence = modjamProjectReleasePersistence;
    }

    public void revertProjectToDraft(String id, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_STATUS_REVERT",
                "You do not have permission to revert this project.");
        if (project.getStatus() != ProjectStatus.PENDING) {
            throw new InvalidProjectRequestException(
                    "Only projects that are pending review can be reverted to draft.");
        }
        project.setStatus(ProjectStatus.DRAFT);
        scoringService.markProjectRankingDirty(project);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void archiveProject(String id, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_STATUS_ARCHIVE",
                "You do not have permission to archive this project.");
        requireNoActiveEmbargo(project);
        if (project.getStatus() != ProjectStatus.PUBLISHED
                && project.getStatus() != ProjectStatus.UNLISTED
                && project.getStatus() != ProjectStatus.PRIVATE) {
            throw new InvalidProjectRequestException("Only published, unlisted, or private projects can be archived.");
        }
        project.setStatus(ProjectStatus.ARCHIVED);
        project.setModjamPublicationPending(false);
        project.setExpiresAt(null);
        scoringService.markProjectRankingDirty(project);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void unlistProject(String id, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_STATUS_UNLIST",
                "You do not have permission to unlist this project.");
        requireNoActiveEmbargo(project);
        if (project.getStatus() != ProjectStatus.PUBLISHED && project.getStatus() != ProjectStatus.ARCHIVED) {
            throw new InvalidProjectRequestException("Only published or archived projects can be unlisted.");
        }
        project.setStatus(ProjectStatus.UNLISTED);
        project.setModjamPublicationPending(false);
        project.setExpiresAt(null);
        scoringService.markProjectRankingDirty(project);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void privateProject(String id, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_STATUS_UNLIST",
                "You do not have permission to make this project private.");
        if (project.getStatus() == ProjectStatus.PENDING || project.getStatus() == ProjectStatus.DELETED) {
            throw new InvalidProjectRequestException("Pending or deleted projects cannot be made private.");
        }
        project.setStatus(ProjectStatus.PRIVATE);
        // An explicit privacy choice cancels automatic jam publication.
        project.setModjamPublicationPending(false);
        project.setExpiresAt(null);
        scoringService.markProjectRankingDirty(project);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void publishProject(String id, User user) {
        Project project = projectAccessService.requireProject(id);
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
        if (project.getVersions() != null && project.getVersions().stream().anyMatch(version ->
                version.getScanResult() != null && version.getScanResult().getStatus() == ScanStatus.SCANNING)) {
            throw new VersionStateConflictException("Wait for the project scan to finish before publishing.");
        }
        boolean embargoed = modjamEmbargoService.hasActiveEmbargo(project);
        project.setStatus(embargoed ? ProjectStatus.PRIVATE : ProjectStatus.PUBLISHED);
        project.setModjamPublicationPending(embargoed);
        project.setExpiresAt(null);
        project.setUpdatedAt(LocalDateTime.now().toString());
        scoringService.markProjectRankingDirty(project);

        if (project.getVersions() != null) {
            project.getVersions().forEach(version -> {
                if (version.getReviewStatus() == ProjectVersion.ReviewStatus.PENDING
                        || version.getReviewStatus() == ProjectVersion.ReviewStatus.SCHEDULED) {
                    version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
                    version.setScheduledPublishDate(null);
                }
            });
            securityIssueAnalysisService.pruneApprovedScanResults(project);
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

        Project saved = projectRepository.save(project);
        projectService.evictProjectCache(saved);

        if (isNew && !embargoed) {
            projectNotificationService.notifyNewProject(saved);
            webhookService.triggerWebhook(saved);
            webhookService.triggerDiscordWebhook(saved);
            trackingService.logNewProject(saved.getId());
        }
    }

    public void releaseModjamEmbargo(Project project) {
        if (!project.isModjamPublicationPending() || project.getStatus() != ProjectStatus.PRIVATE
                || project.getDeletedAt() != null || modjamEmbargoService.hasActiveEmbargo(project)) return;
        if (project.getClassification() != net.modtale.model.project.ProjectClassification.MODPACK
                && (project.getVersions() == null || project.getVersions().stream().noneMatch(version ->
                version != null && version.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED))) return;
        if (project.getVersions() != null && project.getVersions().stream().anyMatch(version ->
                version != null && version.getScanResult() != null
                        && version.getScanResult().getStatus() == ScanStatus.SCANNING)) return;
        Project saved = modjamProjectReleasePersistence.claimRelease(project);
        if (saved == null) return;
        projectService.evictProjectCache(saved);
        projectNotificationService.notifyNewProject(saved);
        webhookService.triggerWebhook(saved);
        webhookService.triggerDiscordWebhook(saved);
        trackingService.logNewProject(saved.getId());
    }

    public void updateProjectStatus(String id, ProjectStatus status, User user, String permissionRequired) {
        Project project = projectAccessService.requireProjectPermission(id, user, permissionRequired,
                "You do not have permission to update this project.");
        if (status == ProjectStatus.PUBLISHED || status == ProjectStatus.UNLISTED || status == ProjectStatus.ARCHIVED) {
            requireNoActiveEmbargo(project);
        }
        project.setStatus(status);
        project.setModjamPublicationPending(false);
        project.setExpiresAt(null);
        scoringService.markProjectRankingDirty(project);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    private void requireNoActiveEmbargo(Project project) {
        if (modjamEmbargoService.hasActiveEmbargo(project)) {
            throw new InvalidProjectRequestException("This jam hides its entries until voting opens.");
        }
    }
}
