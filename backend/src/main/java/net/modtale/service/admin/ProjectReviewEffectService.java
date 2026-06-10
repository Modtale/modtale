package net.modtale.service.admin;

import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.communication.ProjectNotificationService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

@Service
public class ProjectReviewEffectService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ProjectNotificationService projectNotificationService;
    private final AdminAuditLogger adminAuditLogger;

    public ProjectReviewEffectService(
            UserRepository userRepository,
            NotificationService notificationService,
            ProjectNotificationService projectNotificationService,
            AdminAuditLogger adminAuditLogger
    ) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.projectNotificationService = projectNotificationService;
        this.adminAuditLogger = adminAuditLogger;
    }

    public void onProjectPublished(User adminUser, String projectId) {
        adminAuditLogger.logAction(adminUser.getId(), "PUBLISH_PROJECT", projectId, "PROJECT", null);
    }

    public void onVersionApproved(User adminUser, String projectId, String versionId,
                                  ProjectReviewTransitionService.VersionReviewDecision decision) {
        projectNotificationService.notifyUpdates(decision.project(), decision.version().getVersionNumber());
        projectNotificationService.notifyDependents(decision.project(), decision.version().getVersionNumber());
        adminAuditLogger.logAction(adminUser.getId(), "APPROVE_VERSION", projectId, "VERSION", "VerID: " + versionId);
    }

    public void onVersionRejected(User adminUser, String projectId, String versionId,
                                  ProjectReviewTransitionService.VersionReviewDecision decision) {
        User author = findAuthor(decision.project());
        if (author != null) {
            notificationService.sendNotifcation(
                    List.of(author.getId()),
                    "Version Rejected",
                    "Version " + decision.version().getVersionNumber() + " of " + decision.project().getTitle()
                            + " was rejected. Reason: " + decision.reason(),
                    URI.create("/dashboard/projects"),
                    decision.project().getImageUrl()
            );
        }

        adminAuditLogger.logAction(adminUser.getId(), "REJECT_VERSION", projectId, "VERSION",
                "VerID: " + versionId + ", Reason: " + decision.reason());
    }

    public void onProjectRejected(User adminUser, String projectId,
                                  ProjectReviewTransitionService.ProjectRejectionDecision decision) {
        User author = findAuthor(decision.project());
        if (author != null) {
            notificationService.sendNotifcation(
                    List.of(author.getId()),
                    "Project Returned",
                    "Submission '" + decision.project().getTitle() + "' returned to drafts. Reason: "
                            + (decision.reason() != null ? decision.reason() : "Quality Standards"),
                    URI.create("/dashboard/projects"),
                    decision.project().getImageUrl()
            );
        }

        adminAuditLogger.logAction(adminUser.getId(), "REJECT_PROJECT", projectId, "PROJECT",
                "Reason: " + decision.reason());
    }

    private User findAuthor(Project project) {
        if (project.getAuthorId() == null) {
            return null;
        }
        return userRepository.findById(project.getAuthorId()).orElse(null);
    }
}
