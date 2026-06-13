package net.modtale.service.security.access;

import java.util.List;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.user.account.AccountService;
import net.modtale.util.MongoIdUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("apiSecurity")
public class AccessControlService {
    private static final String LEGACY_SUPER_ADMIN_ID = "692620f7c2f3266e23ac0ded";

    private final AccountService accountService;
    private final UserRepository userRepository;
    private final PermissionProjectLookupService permissionProjectLookupService;
    private final MongoTemplate mongoTemplate;

    public AccessControlService(
            @Lazy AccountService accountService,
            UserRepository userRepository,
            PermissionProjectLookupService permissionProjectLookupService,
            MongoTemplate mongoTemplate
    ) {
        this.accountService = accountService;
        this.userRepository = userRepository;
        this.permissionProjectLookupService = permissionProjectLookupService;
        this.mongoTemplate = mongoTemplate;
    }

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

    public boolean isAdmin(Authentication authentication) {
        return isAdmin(accountService.getCurrentUser(authentication));
    }

    public boolean isSuperAdmin(Authentication authentication) {
        return isSuperAdmin(accountService.getCurrentUser(authentication));
    }

    public boolean hasAnyPerm(String perm, Authentication authentication) {
        if (isPublicReadPermission(perm)) return true;
        if (isApiKey(authentication)) {
            return hasApiKeyScope(authentication, "PERSONAL", perm);
        }
        return accountService.getCurrentUser(authentication) != null;
    }

    public boolean hasPersonalPerm(String permStr, Authentication authentication) {
        User user = accountService.getCurrentUser(authentication);
        if (user == null) return false;
        if (isApiKey(authentication)) {
            return hasApiKeyScope(authentication, "PERSONAL", permStr);
        }
        return true;
    }

    public boolean isApiKey(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_API".equals(authority.getAuthority()));
    }

    public boolean hasOrgPerm(String orgId, String permStr, Authentication authentication) {
        User user = accountService.getCurrentUser(authentication);
        if (user == null) return false;
        if (isApiKey(authentication)) {
            return hasApiKeyScope(authentication, orgId, permStr);
        }
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
        User user = accountService.getCurrentUser(authentication);
        if (user == null) return false;

        if (isApiKey(authentication)) {
            if (ownerId == null || ownerId.isEmpty() || ownerId.equals(user.getId())) {
                return hasApiKeyScope(authentication, "PERSONAL", ApiKey.ApiPermission.PROJECT_CREATE.name());
            }

            User org = userRepository.findById(ownerId).orElse(null);
            return org != null
                    && org.getAccountType() == User.AccountType.ORGANIZATION
                    && hasApiKeyScope(authentication, ownerId, ApiKey.ApiPermission.PROJECT_CREATE.name())
                    && hasOrgProjectManagementAccess(org, user.getId());
        }

        if (isAdmin(user)) return true;
        if (ownerId == null || ownerId.isEmpty() || ownerId.equals(user.getId())) return true;
        User org = userRepository.findById(ownerId).orElse(null);
        if (org == null || org.getAccountType() != User.AccountType.ORGANIZATION) return false;

        return hasOrgProjectManagementAccess(org, user.getId());
    }

    public boolean hasProjectPerm(String projectId, String permStr, Authentication authentication) {
        Project project = permissionProjectLookupService.findProject(projectId);
        if (isApiKey(authentication)) {
            if (isProjectReadPermission(permStr) && isPubliclyReadable(project)) {
                return true;
            }
            return hasApiKeyProjectScope(project, projectId, permStr, authentication);
        }

        if (isProjectReadPermission(permStr)) {
            return true;
        }

        if (project == null) return true;

        User user = accountService.getCurrentUser(authentication);
        if (user == null) return false;
        if (isAdmin(user)) return true;

        return hasProjectPermission(project, user, permStr);
    }

    public boolean canReadProject(Project project, User user) {
        if (project == null) return false;
        if (isPubliclyReadable(project)) return true;
        if (user == null) return false;
        if (isAdmin(user)) return true;
        return hasProjectPermission(project, user, "PROJECT_READ");
    }

    public boolean isPubliclyReadable(Project project) {
        return project != null && (
                project.getStatus() == ProjectStatus.PUBLISHED
                        || project.getStatus() == ProjectStatus.UNLISTED
                        || project.getStatus() == ProjectStatus.ARCHIVED
        );
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
        ApiKey.ApiPermission perm = parsePermission(permStr);
        return perm != null && hasProjectPermission(project, user, perm);
    }

    public boolean hasProjectPermission(Project project, User user, ApiKey.ApiPermission perm) {
        return hasProjectPermission(project, user, perm, findAuthorForPermission(project != null ? project.getAuthorId() : null));
    }

    private boolean hasProjectPermission(Project project, User user, ApiKey.ApiPermission perm, User authorUser) {
        if (project == null || user == null) return false;
        if (project.getAuthorId() != null && project.getAuthorId().equals(user.getId())) return true;

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

                if (role != null && role.getPermissions() != null && role.getPermissions().contains(perm)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasEditPermission(Project project, User user) {
        if (project == null || user == null) return false;
        if (project.getAuthorId() != null && project.getAuthorId().equals(user.getId())) return true;

        User authorUser = findAuthorForPermission(project.getAuthorId());
        if (authorUser != null
                && authorUser.getAccountType() == User.AccountType.ORGANIZATION
                && hasOrgProjectManagementAccess(authorUser, user.getId())) {
            return true;
        }
        return hasProjectPermission(project, user, ApiKey.ApiPermission.PROJECT_EDIT_METADATA, authorUser);
    }

    public boolean isOwner(Project project, User user) {
        if (project == null || user == null) return false;
        if (project.getAuthorId() != null && project.getAuthorId().equals(user.getId())) return true;

        User authorUser = findAuthorForPermission(project.getAuthorId());
        if (authorUser != null && authorUser.getAccountType() == User.AccountType.ORGANIZATION) {
            return hasOrgProjectManagementAccess(authorUser, user.getId());
        }
        return false;
    }

    private User findAuthorForPermission(String authorId) {
        if (authorId == null || authorId.isBlank()) return null;

        Query query = new Query(Criteria.where("_id").in(MongoIdUtils.expandIds(List.of(authorId))).and("deletedAt").is(null));
        query.fields()
                .include("_id")
                .include("accountType")
                .include("organizationMembers")
                .include("organizationRoles");
        return mongoTemplate.findOne(query, User.class);
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

    private boolean hasApiKeyProjectScope(Project project, String projectId, String permStr, Authentication authentication) {
        if (project != null) {
            if (hasApiKeyScope(authentication, project.getId(), permStr)) {
                return true;
            }

            User user = accountService.getCurrentUser(authentication);
            if (user != null && project.getAuthorId() != null && project.getAuthorId().equals(user.getId())) {
                return hasApiKeyScope(authentication, "PERSONAL", permStr);
            }

            if (project.getAuthorId() != null && hasApiKeyScope(authentication, project.getAuthorId(), permStr)) {
                return true;
            }
        }

        return hasApiKeyScope(authentication, projectId, permStr);
    }

    private boolean hasApiKeyScope(Authentication authentication, String contextId, String permStr) {
        if (authentication == null || contextId == null || contextId.isBlank() || permStr == null || permStr.isBlank()) {
            return false;
        }

        String expectedAuthority = "SCOPE_" + contextId + "_" + permStr;
        return authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> expectedAuthority.equals(authority.getAuthority()));
    }

    private boolean isProjectReadPermission(String permStr) {
        return "PROJECT_READ".equals(permStr) || "VERSION_READ".equals(permStr);
    }

    private boolean isPublicReadPermission(String permStr) {
        return "PROJECT_READ".equals(permStr)
                || "PROFILE_READ".equals(permStr)
                || "ORG_READ".equals(permStr);
    }

    private ApiKey.ApiPermission parsePermission(String permStr) {
        try {
            return ApiKey.ApiPermission.valueOf(permStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
