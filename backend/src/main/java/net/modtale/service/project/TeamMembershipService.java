package net.modtale.service.project;

import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ProjectOperationForbiddenException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.auth.ApiKeyService;
import net.modtale.service.security.AccessControlService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Service
public class TeamMembershipService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;
    private final ProjectMutationGuard projectMutationGuard;
    private final TeamNotificationService teamNotificationService;
    private final ApiKeyService apiKeyService;
    private final AccessControlService accessControlService;

    public TeamMembershipService(
            ProjectRepository projectRepository,
            UserRepository userRepository,
            ProjectService projectService,
            ProjectAccessService projectAccessService,
            ProjectMutationGuard projectMutationGuard,
            TeamNotificationService teamNotificationService,
            ApiKeyService apiKeyService,
            AccessControlService accessControlService
    ) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.projectService = projectService;
        this.projectAccessService = projectAccessService;
        this.projectMutationGuard = projectMutationGuard;
        this.teamNotificationService = teamNotificationService;
        this.apiKeyService = apiKeyService;
        this.accessControlService = accessControlService;
    }

    public void inviteContributor(String id, String targetUserId, String roleId, User requester) {
        Project project = projectAccessService.requireProjectPermission(id, requester, "PROJECT_TEAM_INVITE",
                "You do not have permission to invite contributors to this project.");
        projectMutationGuard.ensureEditable(project);

        User invitee = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("We couldn't find the contributor you tried to invite."));
        if (project.getAuthorId() != null && project.getAuthorId().equals(invitee.getId())) {
            throw new InvalidProjectRequestException("That account already owns this project.");
        }
        if (project.getTeamMembers() != null && project.getTeamMembers().stream().anyMatch(member -> member.getUserId().equals(invitee.getId()))) {
            throw new InvalidProjectRequestException("That contributor is already on the project team.");
        }
        if (project.getTeamInvites() == null) {
            project.setTeamInvites(new ArrayList<>());
        }
        if (project.getTeamInvites().stream().anyMatch(member -> member.getUserId().equals(invitee.getId()))) {
            throw new InvalidProjectRequestException("That contributor already has a pending invite.");
        }

        project.getTeamInvites().add(new Project.ProjectMember(invitee.getId(), roleId));
        saveProject(project);
        teamNotificationService.sendContributorInvite(project, invitee);
    }

    public void cancelInvite(String id, String targetUserId, User requester) {
        Project project = projectAccessService.requireProjectPermission(id, requester, "PROJECT_TEAM_INVITE",
                "You do not have permission to manage contributor invites for this project.");
        if (project.getTeamInvites() != null) {
            project.getTeamInvites().removeIf(member -> member.getUserId().equals(targetUserId));
            saveProject(project);
        }
    }

    public void updateContributorRole(String id, String targetUserId, String roleId, User requester) {
        Project project = projectAccessService.requireProjectPermission(id, requester, "PROJECT_MEMBER_EDIT_ROLE",
                "You do not have permission to update contributor roles for this project.");
        projectMutationGuard.ensureEditable(project);

        Project.ProjectRole role = project.getProjectRoles().stream()
                .filter(existingRole -> existingRole.getId().equals(roleId))
                .findFirst()
                .orElseThrow(() -> new InvalidProjectRequestException("We couldn't find that project role."));
        if (project.getTeamMembers() != null) {
            Project.ProjectMember member = project.getTeamMembers().stream()
                    .filter(existingMember -> existingMember.getUserId().equals(targetUserId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("We couldn't find that contributor on the project team."));
            member.setRoleId(roleId);
            saveProject(project);
            apiKeyService.syncUserProjectPermissions(targetUserId, id, role.getPermissions());
            teamNotificationService.sendContributorRoleUpdated(project, targetUserId, role);
        }
    }

    public void removeContributor(String id, String targetUserId, User requester) {
        Project project = projectService.getRawProjectById(id);
        if (project != null && (accessControlService.hasProjectPermission(project, requester, "PROJECT_TEAM_REMOVE") || requester.getId().equals(targetUserId))) {
            projectMutationGuard.ensureEditable(project);
            if (project.getTeamMembers() != null && project.getTeamMembers().removeIf(member -> member.getUserId().equals(targetUserId))) {
                apiKeyService.syncUserProjectPermissions(targetUserId, id, EnumSet.noneOf(ApiKey.ApiPermission.class));
            }
            saveProject(project);
            return;
        }
        throw new ProjectOperationForbiddenException("You do not have permission to remove that contributor.");
    }

    public void acceptInvite(String id, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("We couldn't find that user."));
        Project project = projectService.getRawProjectById(id);
        if (project != null && project.getTeamInvites() != null) {
            Project.ProjectMember invite = project.getTeamInvites().stream()
                    .filter(member -> member.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);
            if (invite != null) {
                project.getTeamInvites().remove(invite);
                if (project.getTeamMembers() == null) {
                    project.setTeamMembers(new ArrayList<>());
                }
                project.getTeamMembers().add(invite);
                saveProject(project);

                User owner = userRepository.findById(project.getAuthorId()).orElse(null);
                teamNotificationService.sendInviteAccepted(project, owner, user);
            }
        }
    }

    public void declineInvite(String id, String userId) {
        Project project = projectService.getRawProjectById(id);
        if (project != null && project.getTeamInvites() != null) {
            project.getTeamInvites().removeIf(member -> member.getUserId().equals(userId));
            saveProject(project);
        }
    }

    private void saveProject(Project project) {
        projectRepository.save(project);
        projectService.evictProjectCache(project);
    }
}
