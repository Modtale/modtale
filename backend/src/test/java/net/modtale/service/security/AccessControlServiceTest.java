package net.modtale.service.security;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.user.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessControlServiceTest {

    private AccessControlService accessControlService;
    private AccountService accountService;
    private UserRepository userRepository;
    private ProjectRepository projectRepository;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        userRepository = mock(UserRepository.class);
        projectRepository = mock(ProjectRepository.class);
        accessControlService = new AccessControlService(
                accountService,
                userRepository,
                new PermissionProjectLookupService(projectRepository, new net.modtale.service.project.ProjectRouteService())
        );
    }

    @Test
    void isAdminAndSuperAdminRecognizeLegacyIdsAndRoles() {
        User legacySuperAdmin = user("692620f7c2f3266e23ac0ded", "legacy", List.of("USER"));
        User admin = user("u-admin", "admin", List.of("ADMIN"));
        User plain = user("u-user", "user", List.of("USER"));

        assertTrue(accessControlService.isSuperAdmin(legacySuperAdmin));
        assertTrue(accessControlService.isAdmin(legacySuperAdmin));
        assertTrue(accessControlService.isAdmin(admin));
        assertFalse(accessControlService.isSuperAdmin(admin));
        assertFalse(accessControlService.isAdmin(plain));
    }

    @Test
    void hasOrgPermissionRequiresOrganizationMembershipAndMatchingRolePermissions() {
        User org = organization(
                "org-1",
                "SkyOrg",
                List.of(
                        orgRole("owner", true, Set.of()),
                        orgRole("builder", false, Set.of(ApiKey.ApiPermission.PROJECT_CREATE))
                ),
                List.of(
                        orgMember("u-owner", "owner", null),
                        orgMember("u-builder", "builder", null),
                        orgMember("u-admin-fallback", null, "ADMIN")
                )
        );

        assertTrue(accessControlService.hasOrgPermission(org, "u-owner", ApiKey.ApiPermission.PROJECT_DELETE));
        assertTrue(accessControlService.hasOrgPermission(org, "u-builder", ApiKey.ApiPermission.PROJECT_CREATE));
        assertFalse(accessControlService.hasOrgPermission(org, "u-builder", ApiKey.ApiPermission.PROJECT_DELETE));
        assertFalse(accessControlService.hasOrgPermission(user("u-1", "person", List.of("USER")), "u-builder", ApiKey.ApiPermission.PROJECT_CREATE));
    }

    @Test
    void hasCreateProjectPermAllowsSelfAdminsAndOrganizationManagers() {
        User currentUser = user("u-1", "Ada", List.of("USER"));
        when(accountService.getCurrentUser((Authentication) isNull())).thenReturn(currentUser);

        assertTrue(accessControlService.hasCreateProjectPerm(null, null));
        assertTrue(accessControlService.hasCreateProjectPerm("", null));
        assertTrue(accessControlService.hasCreateProjectPerm("u-1", null));

        User org = organization(
                "org-1",
                "SkyOrg",
                List.of(orgRole("manager", false, Set.of(ApiKey.ApiPermission.PROJECT_EDIT_METADATA))),
                List.of(orgMember("u-1", "manager", null))
        );
        when(userRepository.findById("org-1")).thenReturn(Optional.of(org));
        assertTrue(accessControlService.hasCreateProjectPerm("org-1", null));

        User admin = user("u-admin", "admin", List.of("ADMIN"));
        when(accountService.getCurrentUser((Authentication) isNull())).thenReturn(admin);
        assertTrue(accessControlService.hasCreateProjectPerm("someone-else", null));
    }

    @Test
    void hasAnyPermTreatsPublicReadScopesAsAnonymousSafe() {
        assertTrue(accessControlService.hasAnyPerm("PROJECT_READ", null));
        assertTrue(accessControlService.hasAnyPerm("PROFILE_READ", null));
        assertTrue(accessControlService.hasAnyPerm("ORG_READ", null));
        assertFalse(accessControlService.hasAnyPerm("PROJECT_DELETE", null));
    }

    @Test
    void hasProjectPermAllowsPublishedReadsAndTeamRoleEdits() {
        Project project = new Project();
        project.setId("project-1");
        project.setStatus(ProjectStatus.PUBLISHED);
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));

        assertTrue(accessControlService.hasProjectPerm("project-1", "PROJECT_READ", null));
        assertTrue(accessControlService.hasProjectPerm("project-1", "VERSION_READ", null));

        User currentUser = user("u-editor", "editor", List.of("USER"));
        project.setStatus(ProjectStatus.DRAFT);
        project.setTeamMembers(List.of(new Project.ProjectMember("u-editor", "role-1")));
        project.setProjectRoles(List.of(new Project.ProjectRole("role-1", "Editor", "#fff", Set.of(ApiKey.ApiPermission.PROJECT_EDIT_METADATA))));

        when(accountService.getCurrentUser((Authentication) isNull())).thenReturn(currentUser);

        assertTrue(accessControlService.hasProjectPerm("project-1", "PROJECT_EDIT_METADATA", null));
        assertFalse(accessControlService.hasProjectPerm("project-1", "PROJECT_DELETE", null));
    }

    @Test
    void hasProjectPermResolvesCanonicalSlugRoutes() {
        Project project = new Project();
        project.setId("project-1");
        project.setSlug("levelingcore");
        project.setStatus(ProjectStatus.PUBLISHED);
        when(projectRepository.findBySlug("levelingcore")).thenReturn(Optional.of(project));

        assertTrue(accessControlService.hasProjectPerm("levelingcore", "PROJECT_READ", null));
    }

    @Test
    void ownershipChecksHonorManagingOrganizationMembers() {
        User member = user("u-1", "ada", List.of("USER"));
        User org = organization(
                "org-1",
                "SkyOrg",
                List.of(orgRole("manager", false, Set.of(ApiKey.ApiPermission.PROJECT_CREATE))),
                List.of(orgMember("u-1", "manager", null))
        );
        Project project = new Project();
        project.setAuthorId("org-1");

        when(userRepository.findById("org-1")).thenReturn(Optional.of(org));

        assertTrue(accessControlService.isOwner(project, member));
        assertTrue(accessControlService.hasEditPermission(project, member));
    }

    private static User user(String id, String username, List<String> roles) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRoles(roles);
        return user;
    }

    private static User organization(String id, String username, List<User.OrganizationRole> roles, List<User.OrganizationMember> members) {
        User org = user(id, username, List.of("USER"));
        org.setAccountType(User.AccountType.ORGANIZATION);
        org.setOrganizationRoles(roles);
        org.setOrganizationMembers(members);
        return org;
    }

    private static User.OrganizationRole orgRole(String id, boolean owner, Set<ApiKey.ApiPermission> permissions) {
        User.OrganizationRole role = new User.OrganizationRole(id, "Role " + id, "#fff", permissions);
        role.setOwner(owner);
        return role;
    }

    private static User.OrganizationMember orgMember(String userId, String roleId, String role) {
        User.OrganizationMember member = new User.OrganizationMember(userId, roleId);
        member.setRole(role);
        return member;
    }
}
