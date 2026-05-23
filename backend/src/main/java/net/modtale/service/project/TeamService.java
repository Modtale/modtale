package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.user.NotificationType;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.auth.ApiKeyService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.security.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TeamService {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectService projectService;
    @Autowired private LifecycleService lifecycleService;
    @Autowired private NotificationService notificationService;
    @Autowired private ApiKeyService apiKeyService;
    @Autowired private AccessControlService accessControlService;

    public void requestTransfer(String id, String targetUserId, User requester) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, requester, "PROJECT_TRANSFER_REQUEST")) throw new SecurityException("Permission denied.");
        lifecycleService.ensureEditable(project);

        User target = userRepository.findById(targetUserId).orElseThrow(() -> new IllegalArgumentException("Target not found"));
        if (project.getAuthorId() != null && target.getId().equals(project.getAuthorId())) throw new IllegalArgumentException("Already owned by this entity.");

        project.setPendingTransferTo(target.getUsername());
        projectRepository.save(project);
        projectService.evictProjectCache(project);

        Map<String, String> meta = new HashMap<>();
        meta.put("projectId", project.getId());
        meta.put("action", "TRANSFER_REQUEST");

        User author = userRepository.findById(project.getAuthorId()).orElse(null);
        notificationService.sendActionableNotification(List.of(target.getId()), "Transfer Request", (author != null ? author.getUsername() : "Someone") + " wants to transfer '" + project.getTitle() + "' to you.", URI.create("/dashboard/projects"), project.getImageUrl(), NotificationType.TRANSFER_REQUEST, meta);
    }

    public void resolveTransfer(String id, boolean accept, User responder) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || project.getPendingTransferTo() == null) throw new IllegalArgumentException("Invalid request.");
        lifecycleService.ensureEditable(project);

        if (!responder.getUsername().equalsIgnoreCase(project.getPendingTransferTo())) {
            User tUser = userRepository.findByUsername(project.getPendingTransferTo()).orElse(null);
            if (tUser == null || tUser.getAccountType() != User.AccountType.ORGANIZATION || tUser.getOrganizationMembers().stream().noneMatch(m -> m.getUserId().equals(responder.getId()) && "ADMIN".equalsIgnoreCase(m.getRole()))) {
                throw new SecurityException("Unauthorized.");
            }
        }

        if (accept) {
            User oldOwner = userRepository.findById(project.getAuthorId()).orElse(null);
            User newOwner = userRepository.findByUsernameIgnoreCase(project.getPendingTransferTo()).orElseThrow();

            if (oldOwner != null) {
                if (oldOwner.getAccountType() == User.AccountType.ORGANIZATION && oldOwner.getOrganizationMembers() != null) {
                    oldOwner.getOrganizationMembers().forEach(m -> apiKeyService.syncUserProjectPermissions(m.getUserId(), project.getId(), new ArrayList<>()));
                } else apiKeyService.syncUserProjectPermissions(oldOwner.getId(), project.getId(), new ArrayList<>());
            }

            project.setAuthorId(newOwner.getId());
            project.setAuthor(null);
            project.setPendingTransferTo(null);
            if (project.getTeamMembers() != null) project.getTeamMembers().removeIf(m -> m.getUserId().equals(newOwner.getId()));

            projectRepository.save(project);
            projectService.evictProjectCache(project);
            if(oldOwner != null) notificationService.sendNotification(List.of(oldOwner.getId()), "Transfer Accepted", project.getTitle() + " transferred to " + newOwner.getUsername(), URI.create("/projects/" + project.getId()), project.getImageUrl());
        } else {
            project.setPendingTransferTo(null);
            projectRepository.save(project);
            projectService.evictProjectCache(project);
            User oldOwner = userRepository.findById(project.getAuthorId()).orElse(null);
            if(oldOwner != null) notificationService.sendNotification(List.of(oldOwner.getId()), "Transfer Declined", "Transfer declined for " + project.getTitle(), URI.create("/dashboard/projects"), project.getImageUrl());
        }
    }

    public Project createProjectRole(String id, String name, String color, List<String> perms, User requester) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, requester, "PROJECT_MEMBER_EDIT_ROLE")) throw new SecurityException("Denied");
        if (project.getProjectRoles() == null) project.setProjectRoles(new ArrayList<>());
        if (project.getProjectRoles().size() >= 20) throw new IllegalArgumentException("Max 20 roles reached.");

        project.getProjectRoles().add(new Project.ProjectRole(UUID.randomUUID().toString(), name, color, perms));
        projectRepository.save(project);
        projectService.evictProjectCache(project);
        return project;
    }

    public Project updateProjectRole(String id, String roleId, String name, String color, List<String> perms, User requester) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, requester, "PROJECT_MEMBER_EDIT_ROLE")) throw new SecurityException("Denied");

        Project.ProjectRole role = project.getProjectRoles().stream().filter(r -> r.getId().equals(roleId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Role not found"));
        if (name != null) role.setName(name);
        if (color != null) role.setColor(color);
        if (perms != null) {
            role.setPermissions(perms);
            if (project.getTeamMembers() != null) {
                project.getTeamMembers().stream().filter(m -> roleId.equals(m.getRoleId())).forEach(m -> apiKeyService.syncUserProjectPermissions(m.getUserId(), id, perms));
            }
        }
        projectRepository.save(project);
        projectService.evictProjectCache(project);
        return project;
    }

    public Project deleteProjectRole(String id, String roleId, User requester) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, requester, "PROJECT_MEMBER_EDIT_ROLE")) throw new SecurityException("Denied");

        boolean inUse = (project.getTeamMembers() != null && project.getTeamMembers().stream().anyMatch(m -> roleId.equals(m.getRoleId()))) ||
                (project.getTeamInvites() != null && project.getTeamInvites().stream().anyMatch(m -> roleId.equals(m.getRoleId())));
        if (inUse) throw new IllegalArgumentException("Role in use.");

        project.getProjectRoles().removeIf(r -> r.getId().equals(roleId));
        projectRepository.save(project);
        projectService.evictProjectCache(project);
        return project;
    }

    public void inviteContributor(String id, String targetUserId, String roleId, User requester) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, requester, "PROJECT_TEAM_INVITE")) throw new SecurityException("Denied.");
        lifecycleService.ensureEditable(project);

        User invitee = userRepository.findById(targetUserId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (project.getAuthorId() != null && project.getAuthorId().equals(invitee.getId())) throw new IllegalArgumentException("Author already in project.");
        if (project.getTeamMembers() != null && project.getTeamMembers().stream().anyMatch(m -> m.getUserId().equals(invitee.getId()))) throw new IllegalArgumentException("Already member.");
        if (project.getTeamInvites() == null) project.setTeamInvites(new ArrayList<>());
        if (project.getTeamInvites().stream().anyMatch(m -> m.getUserId().equals(invitee.getId()))) throw new IllegalArgumentException("Already invited.");

        project.getTeamInvites().add(new Project.ProjectMember(invitee.getId(), roleId));
        projectRepository.save(project);
        projectService.evictProjectCache(project);

        Map<String, String> meta = new HashMap<>(); meta.put("projectId", project.getId()); meta.put("action", "CONTRIBUTOR_INVITE");
        notificationService.sendActionableNotification(List.of(invitee.getId()), "Contributor Invite", "You have been invited to " + project.getTitle(), URI.create("/dashboard/projects"), project.getImageUrl(), NotificationType.CONTRIBUTOR_INVITE, meta);
    }

    public void cancelInvite(String id, String targetUserId, User requester) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, requester, "PROJECT_TEAM_INVITE")) throw new SecurityException("Denied.");
        if (project.getTeamInvites() != null) {
            project.getTeamInvites().removeIf(m -> m.getUserId().equals(targetUserId));
            projectRepository.save(project);
            projectService.evictProjectCache(project);
        }
    }

    public void updateContributorRole(String id, String targetUserId, String roleId, User requester) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || !accessControlService.hasProjectPermission(project, requester, "PROJECT_MEMBER_EDIT_ROLE")) throw new SecurityException("Denied.");
        lifecycleService.ensureEditable(project);

        Project.ProjectRole role = project.getProjectRoles().stream().filter(r -> r.getId().equals(roleId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Role not found"));
        if (project.getTeamMembers() != null) {
            Project.ProjectMember member = project.getTeamMembers().stream().filter(m -> m.getUserId().equals(targetUserId)).findFirst().orElseThrow();
            member.setRoleId(roleId);
            projectRepository.save(project);
            projectService.evictProjectCache(project);
            apiKeyService.syncUserProjectPermissions(targetUserId, id, role.getPermissions());
            notificationService.sendNotification(List.of(targetUserId), "Role Updated", "Role in " + project.getTitle() + " updated to " + role.getName(), URI.create(projectService.getProjectLink(project)), project.getImageUrl());
        }
    }

    public void removeContributor(String id, String targetUserId, User requester) {
        Project project = projectService.getRawProjectById(id);
        if (project != null && (accessControlService.hasProjectPermission(project, requester, "PROJECT_TEAM_REMOVE") || requester.getId().equals(targetUserId))) {
            lifecycleService.ensureEditable(project);
            if (project.getTeamMembers() != null && project.getTeamMembers().removeIf(m -> m.getUserId().equals(targetUserId))) {
                apiKeyService.syncUserProjectPermissions(targetUserId, id, new ArrayList<>());
            }
            projectRepository.save(project);
            projectService.evictProjectCache(project);
        } else throw new SecurityException("Denied.");
    }

    public void acceptInvite(String id, String userId) {
        User user = userRepository.findById(userId).orElseThrow();
        Project project = projectService.getRawProjectById(id);
        if (project != null && project.getTeamInvites() != null) {
            Project.ProjectMember invite = project.getTeamInvites().stream().filter(m -> m.getUserId().equals(userId)).findFirst().orElse(null);
            if (invite != null) {
                project.getTeamInvites().remove(invite);
                if (project.getTeamMembers() == null) project.setTeamMembers(new ArrayList<>());
                project.getTeamMembers().add(invite);
                projectRepository.save(project);
                projectService.evictProjectCache(project);

                User owner = userRepository.findById(project.getAuthorId()).orElse(null);
                if (owner != null) notificationService.sendNotification(List.of(owner.getId()), "Invite Accepted", user.getUsername() + " joined " + project.getTitle(), URI.create(projectService.getProjectLink(project)), user.getAvatarUrl());
            }
        }
    }

    public void declineInvite(String id, String userId) {
        Project project = projectService.getRawProjectById(id);
        if (project != null && project.getTeamInvites() != null) {
            project.getTeamInvites().removeIf(m -> m.getUserId().equals(userId));
            projectRepository.save(project);
            projectService.evictProjectCache(project);
        }
    }
}