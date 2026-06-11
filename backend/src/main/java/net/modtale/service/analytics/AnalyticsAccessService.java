package net.modtale.service.analytics;

import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.user.AccountService;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsAccessService {

    private final AccountService accountService;
    private final AccessControlService accessControlService;

    public AnalyticsAccessService(AccountService accountService, AccessControlService accessControlService) {
        this.accountService = accountService;
        this.accessControlService = accessControlService;
    }

    public String resolveCreatorAnalyticsTargetId(User currentUser, String requestedUserId) {
        if (requestedUserId == null || requestedUserId.isBlank() || requestedUserId.equals(currentUser.getId())) {
            return currentUser.getId();
        }

        User target = accountService.getPublicProfile(requestedUserId);
        if (target == null || target.getAccountType() != User.AccountType.ORGANIZATION) {
            throw new ResourceNotFoundException("We couldn't find an organization with that ID.");
        }

        if (!accessControlService.hasOrgPermission(target, currentUser.getId(), ApiKey.ApiPermission.PROJECT_EDIT_METADATA)) {
            throw new ForbiddenOperationException("You do not have permission to view analytics for this organization.");
        }

        return target.getId();
    }

    public void assertProjectAnalyticsAccess(Project project, User currentUser) {
        if (project != null
                && project.getStatus() == ProjectStatus.DRAFT
                && !accessControlService.hasEditPermission(project, currentUser)) {
            throw new ForbiddenOperationException("You do not have permission to view analytics for this draft project.");
        }
    }
}
