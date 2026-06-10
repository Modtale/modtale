package net.modtale.service.project;

import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ProjectOperationForbiddenException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.communication.ProjectNotificationService;
import net.modtale.service.communication.WebhookService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.security.SecurityIssueAnalysisService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ProjectPublicationService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ProjectNotificationService projectNotificationService;
    private final WebhookService webhookService;
    private final TrackingService trackingService;
    private final AccessControlService accessControlService;
    private final ProjectAccessService projectAccessService;
    private final SecurityIssueAnalysisService securityIssueAnalysisService;

    public ProjectPublicationService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ProjectNotificationService projectNotificationService,
            WebhookService webhookService,
            TrackingService trackingService,
            AccessControlService accessControlService,
            ProjectAccessService projectAccessService,
            SecurityIssueAnalysisService securityIssueAnalysisService
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.projectNotificationService = projectNotificationService;
        this.webhookService = webhookService;
        this.trackingService = trackingService;
        this.accessControlService = accessControlService;
        this.projectAccessService = projectAccessService;
        this.securityIssueAnalysisService = securityIssueAnalysisService;
    }

    public void revertProjectToDraft(String id, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_STATUS_REVERT",
                "You do not have permission to revert this project.");
        if (project.getStatus() != ProjectStatus.PENDING) {
            throw new InvalidProjectRequestException(
                    "Only projects that are pending review can be reverted to draft.");
        }
        project.setStatus(ProjectStatus.DRAFT);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void archiveProject(String id, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_STATUS_ARCHIVE",
                "You do not have permission to archive this project.");
        if (project.getStatus() != ProjectStatus.PUBLISHED
                && project.getStatus() != ProjectStatus.UNLISTED
                && project.getStatus() != ProjectStatus.PRIVATE) {
            throw new InvalidProjectRequestException("Only published, unlisted, or private projects can be archived.");
        }
        project.setStatus(ProjectStatus.ARCHIVED);
        project.setExpiresAt(null);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void unlistProject(String id, User user) {
        Project project = projectAccessService.requireProjectPermission(id, user, "PROJECT_STATUS_UNLIST",
                "You do not have permission to unlist this project.");
        if (project.getStatus() != ProjectStatus.PUBLISHED && project.getStatus() != ProjectStatus.ARCHIVED) {
            throw new InvalidProjectRequestException("Only published or archived projects can be unlisted.");
        }
        project.setStatus(ProjectStatus.UNLISTED);
        project.setExpiresAt(null);
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
        project.setExpiresAt(null);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }

    public void publishProject(String id, User user) {
        Project project = projectAccessService.requireProject(id);
        boolean isAdmin = accessControlService.isAdmin(user);
        boolean isRestoration = project.getStatus() == ProjectStatus.ARCHIVED
                || project.getStatus() == ProjectStatus.UNLISTED
                || project.getStatus() == ProjectStatus.PRIVATE;
        boolean isNew = project.getStatus() == ProjectStatus.PENDING || project.getCreatedAt() == null;

        if (isRestoration && !isNew) {
            if (!accessControlService.hasProjectPermission(project, user, "PROJECT_STATUS_PUBLISH")) {
                throw new ProjectOperationForbiddenException("You do not have permission to republish this project.");
            }
        } else if (!isAdmin) {
            throw new ProjectOperationForbiddenException("Only administrators can publish a new project.");
        }
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setExpiresAt(null);
        project.setUpdatedAt(LocalDateTime.now().toString());

        if (project.getVersions() != null) {
            project.getVersions().forEach(version -> {
                if (version.getReviewStatus() == ProjectVersion.ReviewStatus.PENDING
                        || version.getReviewStatus() == ProjectVersion.ReviewStatus.SCHEDULED) {
                    version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
                    version.setScheduledPublishDate(null);
                }
                if (version.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED) {
                    securityIssueAnalysisService.markIssuesAcceptedForApprovedVersion(version);
                }
            });
        }

        if (isNew) {
            project.setCreatedAt(LocalDateTime.now().toString());
        }
        if (!isRestoration && isAdmin && user != null) {
            project.setApprovedBy(user.getUsername());
        }
        if (project.getImageUrl() == null || project.getImageUrl().isEmpty()) {
            project.setImageUrl("https://modtale.net/assets/favicon.svg");
        }

        Project saved = projectRepository.save(project);
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
        project.setStatus(status);
        project.setExpiresAt(null);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }
}
