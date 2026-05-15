package net.modtale.service.security;

import net.modtale.model.project.Project;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService {

    @Autowired private AccountService accountService;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;

    public boolean isAdmin(User user) {
        return user != null && user.getRoles() != null && user.getRoles().contains("ADMIN");
    }

    public boolean hasOrgPermission(User org, String userId, ApiKey.ApiPermission perm) {
        if (org == null || org.getAccountType() != User.AccountType.ORGANIZATION) return false;

        User.OrganizationMember member = org.getOrganizationMembers().stream()
                .filter(m -> m.getUserId().equals(userId)).findFirst().orElse(null);
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
            if (authorUser.getOrganizationMembers().stream().anyMatch(m -> m.getUserId().equals(user.getId()) && "ADMIN".equals(m.getRole()))) return true;
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
            return authorUser.getOrganizationMembers().stream()
                    .anyMatch(m -> m.getUserId().equals(user.getId()) && "ADMIN".equalsIgnoreCase(m.getRole()));
        }
        return false;
    }
}