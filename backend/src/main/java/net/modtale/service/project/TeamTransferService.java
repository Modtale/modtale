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
public class TeamTransferService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final ProjectAccessService projectAccessService;
    private final ProjectMutationGuard projectMutationGuard;
    private final TeamNotificationService teamNotificationService;
    private final ApiKeyService apiKeyService;
    private final AccessControlService accessControlService;

    public TeamTransferService(
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

    public void requestTransfer(String id, String targetUserId, User requester) {
        Project project = projectAccessService.requireProjectPermission(id, requester, "PROJECT_TRANSFER_REQUEST",
                "You do not have permission to transfer this project.");
        projectMutationGuard.ensureEditable(project);

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("We couldn't find the transfer target."));
        if (project.getAuthorId() != null && target.getId().equals(project.getAuthorId())) {
            throw new InvalidProjectRequestException("This project is already owned by that account.");
        }

        project.setPendingTransferTo(target.getId());
        projectRepository.save(project);
        projectService.evictProjectCache(project);

        User author = userRepository.findById(project.getAuthorId()).orElse(null);
        teamNotificationService.sendTransferRequest(project, target, author);
    }

    public void resolveTransfer(String id, boolean accept, User responder) {
        Project project = projectService.getRawProjectById(id);
        if (project == null || project.getPendingTransferTo() == null) {
            throw new InvalidProjectRequestException("We couldn't find a pending transfer request for that project.");
        }
        projectMutationGuard.ensureEditable(project);

        if (!responder.getId().equals(project.getPendingTransferTo())) {
            User targetUser = userRepository.findById(project.getPendingTransferTo()).orElse(null);
            if (targetUser == null || targetUser.getAccountType() != User.AccountType.ORGANIZATION || (
                    !accessControlService.hasOrgPermission(targetUser, responder.getId(), ApiKey.ApiPermission.PROJECT_EDIT_METADATA) &&
                    !accessControlService.hasOrgPermission(targetUser, responder.getId(), ApiKey.ApiPermission.PROJECT_CREATE)
            )) {
                throw new ProjectOperationForbiddenException("You do not have permission to respond to this transfer request.");
            }
        }

        if (accept) {
            User oldOwner = userRepository.findById(project.getAuthorId()).orElse(null);
            User newOwner = userRepository.findById(project.getPendingTransferTo())
                    .orElseThrow(() -> new ResourceNotFoundException("We couldn't find the transfer target."));

            if (oldOwner != null) {
                if (oldOwner.getAccountType() == User.AccountType.ORGANIZATION && oldOwner.getOrganizationMembers() != null) {
                    oldOwner.getOrganizationMembers().forEach(member ->
                            apiKeyService.syncUserProjectPermissions(member.getUserId(), project.getId(), EnumSet.noneOf(ApiKey.ApiPermission.class))
                    );
                } else {
                    apiKeyService.syncUserProjectPermissions(oldOwner.getId(), project.getId(), EnumSet.noneOf(ApiKey.ApiPermission.class));
                }
            }

            project.setAuthorId(newOwner.getId());
            project.setAuthor(null);
            project.setPendingTransferTo(null);
            if (project.getTeamMembers() != null) {
                project.getTeamMembers().removeIf(member -> member.getUserId().equals(newOwner.getId()));
            }

            projectRepository.save(project);
            projectService.evictProjectCache(project);
            teamNotificationService.sendTransferAccepted(project, oldOwner, newOwner);
            return;
        }

        project.setPendingTransferTo(null);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
        User oldOwner = userRepository.findById(project.getAuthorId()).orElse(null);
        teamNotificationService.sendTransferDeclined(project, oldOwner);
    }

}
