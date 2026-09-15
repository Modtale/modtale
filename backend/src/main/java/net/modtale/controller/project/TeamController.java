package net.modtale.controller.project;

import jakarta.validation.Valid;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.project.ProjectDTO;
import net.modtale.model.dto.request.project.ProjectMemberRequest;
import net.modtale.model.dto.request.project.ProjectRoleRequest;
import net.modtale.model.dto.request.project.ProjectTransferRequest;
import net.modtale.model.dto.request.project.ResolveProjectTransferRequest;
import net.modtale.model.dto.request.project.UpdateProjectMemberRoleRequest;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.service.project.team.TeamService;
import net.modtale.service.user.account.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class TeamController {

    private final TeamService teamService;
    private final AccountService accountService;

    public TeamController(TeamService teamService, AccountService accountService) {
        this.teamService = teamService;
        this.accountService = accountService;
    }

    @PostMapping("/roles")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#projectId, 'PROJECT_MEMBER_EDIT_ROLE', authentication)")
    public ResponseEntity<ProjectDTO> createProjectRole(
            @PathVariable String projectId,
            @Valid @RequestBody ProjectRoleRequest requestPayload
    ) {
        User user = accountService.requireCurrentUser("creating a project role");
        Project project = teamService.createProjectRole(
                projectId,
                requestPayload.getName(),
                requestPayload.getColor(),
                requestPayload.getPermissions(),
                user
        );
        return ResponseEntity.ok(ProjectMapper.toDTO(project, false));
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#projectId, 'PROJECT_MEMBER_EDIT_ROLE', authentication)")
    public ResponseEntity<ProjectDTO> updateProjectRole(
            @PathVariable String projectId,
            @PathVariable String roleId,
            @Valid @RequestBody ProjectRoleRequest requestPayload
    ) {
        User user = accountService.requireCurrentUser("updating a project role");
        Project project = teamService.updateProjectRole(
                projectId,
                roleId,
                requestPayload.getName(),
                requestPayload.getColor(),
                requestPayload.getPermissions(),
                user
        );
        return ResponseEntity.ok(ProjectMapper.toDTO(project, false));
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#projectId, 'PROJECT_MEMBER_EDIT_ROLE', authentication)")
    public ResponseEntity<ProjectDTO> deleteProjectRole(@PathVariable String projectId, @PathVariable String roleId) {
        User user = accountService.requireCurrentUser("deleting a project role");
        Project project = teamService.deleteProjectRole(projectId, roleId, user);
        return ResponseEntity.ok(ProjectMapper.toDTO(project, false));
    }

    @PostMapping("/invite")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#projectId, 'PROJECT_TEAM_INVITE', authentication)")
    public ResponseEntity<Void> inviteContributor(
            @PathVariable String projectId,
            @Valid @RequestBody ProjectMemberRequest requestPayload
    ) {
        User user = accountService.requireCurrentUser("inviting a project contributor");
        teamService.inviteContributor(projectId, requestPayload.getUserId(), requestPayload.getRoleId(), user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/invites/{userId}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#projectId, 'PROJECT_TEAM_INVITE', authentication)")
    public ResponseEntity<Void> cancelInvite(@PathVariable String projectId, @PathVariable String userId) {
        User user = accountService.requireCurrentUser("canceling a project invite");
        teamService.cancelInvite(projectId, userId, user);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/contributors/{userId}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#projectId, 'PROJECT_MEMBER_EDIT_ROLE', authentication)")
    public ResponseEntity<Void> updateContributorRole(
            @PathVariable String projectId,
            @PathVariable String userId,
            @Valid @RequestBody UpdateProjectMemberRoleRequest requestPayload
    ) {
        User user = accountService.requireCurrentUser("updating a project contributor role");
        teamService.updateContributorRole(projectId, userId, requestPayload.getRoleId(), user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/contributors/{userId}")
    public ResponseEntity<Void> removeContributor(@PathVariable String projectId, @PathVariable String userId) {
        User user = accountService.requireCurrentUser("removing a project contributor");
        teamService.removeContributor(projectId, userId, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/invite/accept")
    public ResponseEntity<Void> acceptInvite(@PathVariable String projectId) {
        User user = accountService.requireCurrentUser("accepting a project invite");
        teamService.acceptInvite(projectId, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/invite/decline")
    public ResponseEntity<Void> declineInvite(@PathVariable String projectId) {
        User user = accountService.requireCurrentUser("declining a project invite");
        teamService.declineInvite(projectId, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transfer")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#projectId, 'PROJECT_TRANSFER_REQUEST', authentication)")
    public ResponseEntity<Void> requestTransfer(
            @PathVariable String projectId,
            @Valid @RequestBody ProjectTransferRequest requestPayload
    ) {
        User user = accountService.requireCurrentUser("transferring a project");
        teamService.requestTransfer(projectId, requestPayload.getUserId(), user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/transfer/resolve")
    public ResponseEntity<Void> resolveTransfer(
            @PathVariable String projectId,
            @Valid @RequestBody ResolveProjectTransferRequest requestPayload
    ) {
        User user = accountService.requireCurrentUser("responding to a project transfer");
        teamService.resolveTransfer(projectId, requestPayload.getAccept(), user);
        return ResponseEntity.ok().build();
    }
}
