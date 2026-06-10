package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TeamService {

    private final TeamTransferService teamTransferService;
    private final TeamRoleService teamRoleService;
    private final TeamMembershipService teamMembershipService;

    public TeamService(
            TeamTransferService teamTransferService,
            TeamRoleService teamRoleService,
            TeamMembershipService teamMembershipService
    ) {
        this.teamTransferService = teamTransferService;
        this.teamRoleService = teamRoleService;
        this.teamMembershipService = teamMembershipService;
    }

    public void requestTransfer(String id, String targetUserId, User requester) {
        teamTransferService.requestTransfer(id, targetUserId, requester);
    }

    public void resolveTransfer(String id, boolean accept, User responder) {
        teamTransferService.resolveTransfer(id, accept, responder);
    }

    public Project createProjectRole(String id, String name, String color, List<String> perms, User requester) {
        return teamRoleService.createProjectRole(id, name, color, perms, requester);
    }

    public Project updateProjectRole(String id, String roleId, String name, String color, List<String> perms, User requester) {
        return teamRoleService.updateProjectRole(id, roleId, name, color, perms, requester);
    }

    public Project deleteProjectRole(String id, String roleId, User requester) {
        return teamRoleService.deleteProjectRole(id, roleId, requester);
    }

    public void inviteContributor(String id, String targetUserId, String roleId, User requester) {
        teamMembershipService.inviteContributor(id, targetUserId, roleId, requester);
    }

    public void cancelInvite(String id, String targetUserId, User requester) {
        teamMembershipService.cancelInvite(id, targetUserId, requester);
    }

    public void updateContributorRole(String id, String targetUserId, String roleId, User requester) {
        teamMembershipService.updateContributorRole(id, targetUserId, roleId, requester);
    }

    public void removeContributor(String id, String targetUserId, User requester) {
        teamMembershipService.removeContributor(id, targetUserId, requester);
    }

    public void acceptInvite(String id, String userId) {
        teamMembershipService.acceptInvite(id, userId);
    }

    public void declineInvite(String id, String userId) {
        teamMembershipService.declineInvite(id, userId);
    }
}
