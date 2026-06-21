package net.modtale.service.auth;

import java.util.EnumSet;
import java.util.Set;
import net.modtale.exception.ApiKeyOperationForbiddenException;
import net.modtale.model.project.Project;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.security.access.AccessControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApiKeyContextPermissionService {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyContextPermissionService.class);

    private final AccessControlService accessControlService;

    ApiKeyContextPermissionService(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    Set<ApiKey.ApiPermission> filterOrganizationPermissions(
            User organization,
            User.OrganizationMember membership,
            Set<ApiKey.ApiPermission> requestedPermissions
    ) {
        Set<ApiKey.ApiPermission> allowedPermissions = EnumSet.noneOf(ApiKey.ApiPermission.class);
        if (requestedPermissions == null || requestedPermissions.isEmpty()) {
            return allowedPermissions;
        }
        if ("ADMIN".equals(membership.getRole())) {
            allowedPermissions.addAll(requestedPermissions);
            return allowedPermissions;
        }
        if (membership.getRoleId() == null || organization.getOrganizationRoles() == null) {
            return allowedPermissions;
        }

        User.OrganizationRole role = organization.getOrganizationRoles().stream()
                .filter(candidate -> candidate.getId().equals(membership.getRoleId()))
                .findFirst()
                .orElse(null);

        if (role == null) {
            return allowedPermissions;
        }
        if (role.isOwner()) {
            allowedPermissions.addAll(requestedPermissions);
            return allowedPermissions;
        }
        if (role.getPermissions() == null) {
            return allowedPermissions;
        }

        for (ApiKey.ApiPermission permission : requestedPermissions) {
            if (role.getPermissions().contains(permission)) {
                allowedPermissions.add(permission);
            }
        }
        return allowedPermissions;
    }

    Set<ApiKey.ApiPermission> filterProjectPermissions(
            Project project,
            User user,
            String userId,
            Set<ApiKey.ApiPermission> requestedPermissions
    ) {
        return filterProjectPermissions(project, user, userId, requestedPermissions, true);
    }

    Set<ApiKey.ApiPermission> filterProjectPermissionsOrEmpty(
            Project project,
            User user,
            String userId,
            Set<ApiKey.ApiPermission> requestedPermissions
    ) {
        return filterProjectPermissions(project, user, userId, requestedPermissions, false);
    }

    boolean isActiveOrganization(User organization) {
        return organization != null
                && !organization.isDeleted()
                && organization.getAccountType() == User.AccountType.ORGANIZATION;
    }

    User.OrganizationMember findOrganizationMembership(User organization, String userId) {
        if (organization.getOrganizationMembers() == null) {
            return null;
        }
        return organization.getOrganizationMembers().stream()
                .filter(member -> member.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    private Set<ApiKey.ApiPermission> filterProjectPermissions(
            Project project,
            User user,
            String userId,
            Set<ApiKey.ApiPermission> requestedPermissions,
            boolean requireMembership
    ) {
        Set<ApiKey.ApiPermission> allowedPermissions = EnumSet.noneOf(ApiKey.ApiPermission.class);
        if (requestedPermissions == null || requestedPermissions.isEmpty()) {
            return allowedPermissions;
        }
        if (accessControlService.isOwner(project, user)) {
            allowedPermissions.addAll(requestedPermissions);
            return allowedPermissions;
        }

        Project.ProjectMember member = findProjectMembership(project, userId);
        if (member == null) {
            if (requireMembership) {
                throw new ApiKeyOperationForbiddenException("You are not a contributor on the requested project context.");
            }
            return allowedPermissions;
        }

        return intersectProjectRolePermissions(findProjectRole(project, member.getRoleId()), requestedPermissions, project.getId());
    }

    private Set<ApiKey.ApiPermission> intersectProjectRolePermissions(
            Project.ProjectRole role,
            Set<ApiKey.ApiPermission> requestedPermissions,
            String projectId
    ) {
        Set<ApiKey.ApiPermission> allowedPermissions = EnumSet.noneOf(ApiKey.ApiPermission.class);
        if (role == null || role.getPermissions() == null) {
            return allowedPermissions;
        }

        for (ApiKey.ApiPermission permission : requestedPermissions) {
            if (role.getPermissions().contains(permission)) {
                allowedPermissions.add(permission);
            }
        }
        return allowedPermissions;
    }

    private Project.ProjectMember findProjectMembership(Project project, String userId) {
        if (project.getTeamMembers() == null) {
            return null;
        }
        return project.getTeamMembers().stream()
                .filter(projectMember -> projectMember.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    private Project.ProjectRole findProjectRole(Project project, String roleId) {
        if (project.getProjectRoles() == null || roleId == null) {
            return null;
        }
        return project.getProjectRoles().stream()
                .filter(projectRole -> projectRole.getId().equals(roleId))
                .findFirst()
                .orElse(null);
    }
}
