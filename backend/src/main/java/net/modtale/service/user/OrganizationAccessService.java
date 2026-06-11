package net.modtale.service.user;

import net.modtale.exception.InvalidOrganizationRequestException;
import net.modtale.exception.OrganizationNotFoundException;
import net.modtale.exception.OrganizationOperationForbiddenException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.AccessControlService;
import org.springframework.stereotype.Service;

@Service
public class OrganizationAccessService {

    private final UserRepository userRepository;
    private final AccessControlService accessControlService;

    public OrganizationAccessService(UserRepository userRepository, AccessControlService accessControlService) {
        this.userRepository = userRepository;
        this.accessControlService = accessControlService;
    }

    public User getOrganizationOrThrow(String orgId) {
        User org = userRepository.findById(orgId)
                .orElseThrow(() -> new OrganizationNotFoundException("We couldn't find that organization."));
        if (org.getAccountType() != User.AccountType.ORGANIZATION) {
            throw new InvalidOrganizationRequestException("The requested account is not an organization.");
        }
        return org;
    }

    public boolean hasOrgPermission(User org, String userId, ApiKey.ApiPermission permission) {
        return accessControlService.hasOrgPermission(org, userId, permission);
    }

    public void requireOrgPermission(User org, User requester, ApiKey.ApiPermission permission, String failureMessage) {
        if (requester == null || !hasOrgPermission(org, requester.getId(), permission)) {
            throw new OrganizationOperationForbiddenException(failureMessage);
        }
    }

    public User.OrganizationRole getOrganizationRoleOrThrow(User org, String roleId) {
        return org.getOrganizationRoles().stream()
                .filter(role -> role.getId().equals(roleId))
                .findFirst()
                .orElseThrow(() -> new InvalidOrganizationRequestException("We couldn't find that organization role."));
    }

    public User.OrganizationRole getOrganizationRole(User org, String roleId) {
        if (roleId == null || org.getOrganizationRoles() == null) {
            return null;
        }
        return org.getOrganizationRoles().stream()
                .filter(role -> roleId.equals(role.getId()))
                .findFirst()
                .orElse(null);
    }

    public User.OrganizationMember getOrganizationMemberOrThrow(User org, String userId, String failureMessage) {
        return org.getOrganizationMembers().stream()
                .filter(member -> member.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new InvalidOrganizationRequestException(failureMessage));
    }
}
