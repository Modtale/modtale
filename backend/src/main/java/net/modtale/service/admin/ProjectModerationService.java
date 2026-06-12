package net.modtale.service.admin;

import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.project.ProjectDeletionService;
import net.modtale.service.project.ProjectRetentionService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.project.ProjectVersionAccessService;
import net.modtale.service.security.ScanService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

@Service
public class ProjectModerationService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ProjectRetentionService projectRetentionService;
    private final ProjectDeletionService projectDeletionService;
    private final ScoringService scoringService;
    private final NotificationService notificationService;
    private final ScanService scanService;
    private final ProjectVersionAccessService projectVersionAccessService;
    private final AdminAuditLogger adminAuditLogger;

    public ProjectModerationService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ProjectRetentionService projectRetentionService,
            ProjectDeletionService projectDeletionService,
            ScoringService scoringService,
            NotificationService notificationService,
            ScanService scanService,
            ProjectVersionAccessService projectVersionAccessService,
            AdminAuditLogger adminAuditLogger
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.projectRetentionService = projectRetentionService;
        this.projectDeletionService = projectDeletionService;
        this.scoringService = scoringService;
        this.notificationService = notificationService;
        this.scanService = scanService;
        this.projectVersionAccessService = projectVersionAccessService;
        this.adminAuditLogger = adminAuditLogger;
    }

    public void deleteProject(net.modtale.model.user.User adminUser, String id, String reason) {
        Project targetProject = requireProject(id);
        projectRetentionService.softDelete(targetProject);
        notificationService.sendNotifcation(
                List.of(targetProject.getAuthorId()),
                "Project Deleted",
                "Your project '" + targetProject.getTitle() + "' was deleted by an administrator. Reason: " + reason,
                URI.create("/dashboard"),
                null
        );
        adminAuditLogger.logAction(adminUser.getId(), "DELETE_PROJECT", id, "PROJECT", "Reason: " + reason);
    }

    public void hardDeleteProject(net.modtale.model.user.User adminUser, String id, String reason) {
        Project targetProject = requireProject(id);
        projectRetentionService.hardDelete(targetProject);
        notificationService.sendNotifcation(
                List.of(targetProject.getAuthorId()),
                "Project Permanently Deleted",
                "Your project '" + targetProject.getTitle() + "' was permanently removed by an administrator. Reason: " + reason,
                URI.create("/dashboard"),
                null
        );
        adminAuditLogger.logAction(adminUser.getId(), "HARD_DELETE_PROJECT", id, "PROJECT", "Reason: " + reason);
    }

    public void restoreProject(net.modtale.model.user.User adminUser, String id, ProjectStatus targetStatus) {
        Project project = requireProject(id);
        projectRetentionService.restore(project, targetStatus);
        adminAuditLogger.logAction(adminUser.getId(), "RESTORE_PROJECT", id, "PROJECT", "To Status: " + targetStatus);
    }

    public void unlistProject(net.modtale.model.user.User adminUser, String id, String reason) {
        Project targetProject = requireProject(id);
        targetProject.setStatus(ProjectStatus.UNLISTED);
        targetProject.setExpiresAt(null);
        scoringService.markProjectRankingDirty(targetProject);
        projectRepository.save(targetProject);
        projectService.evictProjectCache(targetProject);

        notificationService.sendNotifcation(
                List.of(targetProject.getAuthorId()),
                "Project Unlisted",
                "Your project '" + targetProject.getTitle() + "' was unlisted from the public directory by an administrator. Reason: " + reason,
                URI.create(projectService.getProjectLink(targetProject)),
                null
        );
        adminAuditLogger.logAction(adminUser.getId(), "UNLIST_PROJECT", id, "PROJECT", "Reason: " + reason);
    }

    public void deleteProjectVersion(net.modtale.model.user.User adminUser, String id, String versionId) {
        Project project = requireProject(id);
        projectVersionAccessService.requireById(project, versionId,
                () -> new ResourceNotFoundException("Version not found."));
        project.getVersions().removeIf(version -> {
            if (!version.getId().equals(versionId)) {
                return false;
            }
            projectDeletionService.deleteVersionFile(version);
            return true;
        });

        projectRepository.save(project);
        projectService.evictProjectCache(project);
        adminAuditLogger.logAction(adminUser.getId(), "DELETE_VERSION", id, "VERSION", "VerID: " + versionId);
    }

    public void rescanVersion(net.modtale.model.user.User adminUser, String id, String versionId) {
        scanService.triggerRescan(id, versionId, adminUser);
        adminAuditLogger.logAction(adminUser.getId(), "RESCAN_VERSION", id, "VERSION", "VerID: " + versionId);
    }

    private Project requireProject(String id) {
        Project project = projectService.getRawProjectById(id);
        if (project == null) {
            throw new ResourceNotFoundException("Project not found.");
        }
        return project;
    }
}
