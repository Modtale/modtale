package net.modtale.service.security;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("apiSecurity")
public class AccessControlService {
    private static final String LEGACY_SUPER_ADMIN_ID = "692620f7c2f3266e23ac0ded";

    @Autowired private AccountService accountService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;

    public boolean isAdmin(User user) {
        return user != null && (
                isSuperAdmin(user) ||
                        user.getRoles() != null && user.getRoles().contains("ADMIN")
        );
    }

    public boolean isSuperAdmin(User user) {
        return user != null && (
                user.getRoles() != null && user.getRoles().contains("SUPER_ADMIN")
                        || LEGACY_SUPER_ADMIN_ID.equals(user.getId())
        );
    }

    public boolean hasAnyPerm(String perm, Authentication authentication) {
        if ("PROJECT_READ".equals(perm)) return true;
        return accountService.getCurrentUser() != null;
    }

    public boolean hasPersonalPerm(String permStr, Authentication authentication) {
        User user = accountService.getCurrentUser();
        return user != null;
    }

    public boolean hasOrgPerm(String orgId, String permStr, Authentication authentication) {
        User user = accountService.getCurrentUser();
        if (user == null) return false;
        if (isAdmin(user)) return true;

        User org = userRepository.findById(orgId).orElse(null);
        if (org == null) return false;

        try {
            ApiKey.ApiPermission perm = ApiKey.ApiPermission.valueOf(permStr);
            return hasOrgPermission(org, user.getId(), perm);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean hasCreateProjectPerm(String ownerId, Authentication authentication) {
        User user = accountService.getCurrentUser();
        if (user == null) return false;
        if (isAdmin(user)) return true;
        if (ownerId == null || ownerId.isEmpty() || ownerId.equals(user.getId())) return true;

        User org = userRepository.findById(ownerId).orElse(null);
        if (org == null || org.getAccountType() != User.AccountType.ORGANIZATION) return false;

        return hasOrgProjectManagementAccess(org, user.getId());
    }

    public boolean hasProjectPerm(String projectId, String permStr, Authentication authentication) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return true;

        if ("PROJECT_READ".equals(permStr)) {
            if (project.getStatus() == ProjectStatus.PUBLISHED ||
                    project.getStatus() == ProjectStatus.UNLISTED ||
                    project.getStatus() == ProjectStatus.ARCHIVED) {
                return true;
            }
        }

        User user = accountService.getCurrentUser();
        if (user == null) return false;
        if (isAdmin(user)) return true;

        return hasProjectPermission(project, user, permStr);
    }

    public boolean hasOrgPermission(User org, String userId, ApiKey.ApiPermission perm) {
        if (org == null || org.getAccountType() != User.AccountType.ORGANIZATION) return false;

        User.OrganizationMember member = getOrgMember(org, userId);
        if (member == null) return false;

        if (member.getRoleId() != null) {
            User.OrganizationRole role = org.getOrganizationRoles().stream()
                    .filter(r -> r.getId().equals(member.getRoleId())).findFirst().orElse(null);
            if (role != null) {
                if (role.isOwner()) return true;
                if (role.getPermissions() != null) return role.getPermissions().contains(perm);
            }
        }
        return false;
    }

    public boolean hasProjectPermission(Project project, User user, String permStr) {
        if (project == null || user == null) return false;
        if (project.getAuthorId() != null && project.getAuthorId().equals(user.getId())) return true;

        ApiKey.ApiPermission perm;
        try {
            perm = ApiKey.ApiPermission.valueOf(permStr);
        } catch (IllegalArgumentException e) {
            return false;
        }

        User authorUser = accountService.getPublicProfile(project.getAuthorId());
        if (authorUser != null && authorUser.getAccountType() == User.AccountType.ORGANIZATION) {
            if (hasOrgPermission(authorUser, user.getId(), perm)) return true;
            if (hasOrgProjectManagementAccess(authorUser, user.getId())) return true;
        }

        if (project.getTeamMembers() != null) {
            Project.ProjectMember member = project.getTeamMembers().stream()
                    .filter(m -> m.getUserId().equals(user.getId())).findFirst().orElse(null);

            if (member != null && member.getRoleId() != null && project.getProjectRoles() != null) {
                Project.ProjectRole role = project.getProjectRoles().stream()
                        .filter(r -> r.getId().equals(member.getRoleId())).findFirst().orElse(null);

                if (role != null && role.getPermissions() != null && role.getPermissions().contains(permStr)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasEditPermission(Project project, User user) {
        return isOwner(project, user) || hasProjectPermission(project, user, "PROJECT_EDIT_METADATA");
    }

    public boolean isOwner(Project project, User user) {
        if (project == null || user == null) return false;
        if (project.getAuthorId() != null && project.getAuthorId().equals(user.getId())) return true;

        User authorUser = accountService.getPublicProfile(project.getAuthorId());
        if (authorUser != null && authorUser.getAccountType() == User.AccountType.ORGANIZATION) {
            return hasOrgProjectManagementAccess(authorUser, user.getId());
        }
        return false;
    }

    private User.OrganizationMember getOrgMember(User org, String userId) {
        if (org == null || org.getOrganizationMembers() == null || userId == null) return null;
        return org.getOrganizationMembers().stream()
                .filter(m -> userId.equals(m.getUserId()))
                .findFirst()
                .orElse(null);
    }

    private User.OrganizationRole getOrgRole(User org, User.OrganizationMember member) {
        if (org == null || member == null || member.getRoleId() == null || org.getOrganizationRoles() == null) return null;
        return org.getOrganizationRoles().stream()
                .filter(r -> member.getRoleId().equals(r.getId()))
                .findFirst()
                .orElse(null);
    }

    private boolean hasOrgProjectManagementAccess(User org, String userId) {
        User.OrganizationMember member = getOrgMember(org, userId);
        if (member == null) return false;

        User.OrganizationRole role = getOrgRole(org, member);
        if (role != null) {
            if (role.isOwner()) return true;
            return role.getPermissions() != null && (
                    role.getPermissions().contains(ApiKey.ApiPermission.PROJECT_EDIT_METADATA) ||
                            role.getPermissions().contains(ApiKey.ApiPermission.PROJECT_CREATE)
            );
        }

        return "ADMIN".equalsIgnoreCase(member.getRole());
    }
}
