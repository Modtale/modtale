package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.user.NotificationType;
import net.modtale.model.user.User;
import net.modtale.service.communication.NotificationService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
public class TeamNotificationService {

    private final NotificationService notificationService;
    private final ProjectService projectService;

    public TeamNotificationService(NotificationService notificationService, ProjectService projectService) {
        this.notificationService = notificationService;
        this.projectService = projectService;
    }

    public void sendContributorInvite(Project project, User invitee) {
        notificationService.sendNotifcation(
                List.of(invitee.getId()),
                "Contributor Invite",
                "You have been invited to " + project.getTitle(),
                URI.create("/dashboard/projects"),
                project.getImageUrl(),
                NotificationType.CONTRIBUTOR_INVITE,
                Map.of("projectId", project.getId(), "action", "CONTRIBUTOR_INVITE")
        );
    }

    public void sendContributorRoleUpdated(Project project, String targetUserId, Project.ProjectRole role) {
        notificationService.sendNotifcation(
                List.of(targetUserId),
                "Role Updated",
                "Role in " + project.getTitle() + " updated to " + role.getName(),
                URI.create(projectService.getProjectLink(project)),
                project.getImageUrl()
        );
    }

    public void sendInviteAccepted(Project project, User owner, User invitee) {
        if (owner == null) {
            return;
        }

        notificationService.sendNotifcation(
                List.of(owner.getId()),
                "Invite Accepted",
                invitee.getUsername() + " joined " + project.getTitle(),
                URI.create(projectService.getProjectLink(project)),
                invitee.getAvatarUrl()
        );
    }

    public void sendTransferRequest(Project project, User targetUser, User author) {
        String authorName = author != null ? author.getUsername() : "Someone";
        notificationService.sendNotifcation(
                List.of(targetUser.getId()),
                "Transfer Request",
                authorName + " wants to transfer '" + project.getTitle() + "' to you.",
                URI.create("/dashboard/projects"),
                project.getImageUrl(),
                NotificationType.TRANSFER_REQUEST,
                Map.of("projectId", project.getId(), "action", "TRANSFER_REQUEST")
        );
    }

    public void sendTransferAccepted(Project project, User oldOwner, User newOwner) {
        if (oldOwner == null) {
            return;
        }

        notificationService.sendNotifcation(
                List.of(oldOwner.getId()),
                "Transfer Accepted",
                project.getTitle() + " transferred to " + newOwner.getUsername(),
                URI.create("/projects/" + project.getId()),
                project.getImageUrl()
        );
    }

    public void sendTransferDeclined(Project project, User oldOwner) {
        if (oldOwner == null) {
            return;
        }

        notificationService.sendNotifcation(
                List.of(oldOwner.getId()),
                "Transfer Declined",
                "Transfer declined for " + project.getTitle(),
                URI.create("/dashboard/projects"),
                project.getImageUrl()
        );
    }
}
