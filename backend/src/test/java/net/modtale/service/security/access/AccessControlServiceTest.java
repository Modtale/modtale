package net.modtale.service.security.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.AdminPermission;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.query.ProjectRouteService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessControlServiceTest {

    private AccessControlService accessControlService;
    private AccountService accountService;
    private UserRepository userRepository;
    private ProjectRepository projectRepository;
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        accountService = mock(AccountService.class);
        userRepository = mock(UserRepository.class);
        projectRepository = mock(ProjectRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        accessControlService = new AccessControlService(
                accountService,
                userRepository,
                new PermissionProjectLookupService(projectRepository, new net.modtale.service.project.query.ProjectRouteService()),
                mongoTemplate
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void isAdminRequiresExplicitAdminPermissions() {
        User adminRoleOnly = user("u-admin", "admin", List.of("ADMIN"));
        User superAdminRoleOnly = user("u-super-role", "super-role", List.of("SUPER_ADMIN"));
        User projectAdmin = userWithAdminPermissions("u-project-admin", "project-admin", AdminPermission.PROJECT_MODERATE);
        User fullAdmin = userWithAdminPermissions("u-full-admin", "full-admin", AdminPermission.allPermissions().toArray(AdminPermission[]::new));
        User plain = user("u-user", "user", List.of("USER"));

        assertTrue(accessControlService.isAdmin(fullAdmin));
        assertTrue(accessControlService.isAdmin(projectAdmin));
        assertTrue(accessControlService.hasAdminPermission(projectAdmin, AdminPermission.PROJECT_MODERATE));
        assertFalse(accessControlService.isAdmin(adminRoleOnly));
        assertFalse(accessControlService.isAdmin(superAdminRoleOnly));
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

        User admin = userWithAdminPermissions("u-admin", "admin", AdminPermission.PROJECT_MODERATE);
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
        when(projectRepository.findPermissionSnapshotById("project-1")).thenReturn(Optional.of(project));

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
    void hasProjectPermAllowsOwnersAndAuthorizedTeamMembersToReadDrafts() {
        Project project = new Project();
        project.setId("draft-1");
        project.setAuthorId("u-owner");
        project.setStatus(ProjectStatus.DRAFT);
        project.setTeamMembers(List.of(new Project.ProjectMember("u-reader", "reader-role")));
        project.setProjectRoles(List.of(new Project.ProjectRole(
                "reader-role",
                "Reader",
                "#fff",
                Set.of(ApiKey.ApiPermission.PROJECT_READ)
        )));
        when(projectRepository.findPermissionSnapshotById("draft-1")).thenReturn(Optional.of(project));

        when(accountService.getCurrentUser((Authentication) isNull()))
                .thenReturn(user("u-owner", "owner", List.of("USER")));
        assertTrue(accessControlService.hasProjectPerm("draft-1", "PROJECT_READ", null));

        when(accountService.getCurrentUser((Authentication) isNull()))
                .thenReturn(user("u-reader", "reader", List.of("USER")));
        assertTrue(accessControlService.hasProjectPerm("draft-1", "PROJECT_READ", null));

        when(accountService.getCurrentUser((Authentication) isNull()))
                .thenReturn(user("u-stranger", "stranger", List.of("USER")));
        assertFalse(accessControlService.hasProjectPerm("draft-1", "PROJECT_READ", null));
    }

    @Test
    void hasProjectPermHonorsApiKeyProjectScopesForMutations() {
        Project project = new Project();
        project.setId("project-1");
        project.setStatus(ProjectStatus.DRAFT);
        when(projectRepository.findPermissionSnapshotById("project-1")).thenReturn(Optional.of(project));

        Authentication uploadKey = apiAuthentication(
                user("u-key", "key-user", List.of("USER")),
                "SCOPE_project-1_VERSION_CREATE"
        );

        assertTrue(accessControlService.hasProjectPerm("project-1", "VERSION_CREATE", uploadKey));

        Authentication readOnlyKey = apiAuthentication(
                user("u-key", "key-user", List.of("USER")),
                "SCOPE_project-1_PROJECT_READ"
        );

        assertFalse(accessControlService.hasProjectPerm("project-1", "VERSION_CREATE", readOnlyKey));
    }

    @Test
    void hasProjectPermDoesNotLetApiKeysInheritOwnerProjectAccessWithoutScope() {
        User owner = user("owner-1", "owner", List.of("USER"));
        Project project = new Project();
        project.setId("project-1");
        project.setAuthorId("owner-1");
        project.setStatus(ProjectStatus.DRAFT);
        when(projectRepository.findPermissionSnapshotById("project-1")).thenReturn(Optional.of(project));

        Authentication unscopedKey = apiAuthentication(owner);
        when(accountService.getCurrentUser(unscopedKey)).thenReturn(owner);

        assertFalse(accessControlService.hasProjectPerm("project-1", "VERSION_CREATE", unscopedKey));
    }

    @Test
    void hasProjectPermAllowsApiKeyOrganizationAndPersonalProjectContexts() {
        User owner = user("owner-1", "owner", List.of("USER"));
        Project personalProject = new Project();
        personalProject.setId("personal-project");
        personalProject.setAuthorId("owner-1");
        personalProject.setStatus(ProjectStatus.DRAFT);
        when(projectRepository.findPermissionSnapshotById("personal-project")).thenReturn(Optional.of(personalProject));

        Authentication personalKey = apiAuthentication(owner, "SCOPE_PERSONAL_VERSION_CREATE");
        when(accountService.getCurrentUser(personalKey)).thenReturn(owner);

        assertTrue(accessControlService.hasProjectPerm("personal-project", "VERSION_CREATE", personalKey));

        Project orgProject = new Project();
        orgProject.setId("org-project");
        orgProject.setAuthorId("org-1");
        orgProject.setStatus(ProjectStatus.DRAFT);
        when(projectRepository.findPermissionSnapshotById("org-project")).thenReturn(Optional.of(orgProject));

        Authentication orgKey = apiAuthentication(owner, "SCOPE_org-1_VERSION_CREATE");

        assertTrue(accessControlService.hasProjectPerm("org-project", "VERSION_CREATE", orgKey));
    }

    @Test
    void serviceProjectPermissionHonorsCurrentApiKeyProjectScope() {
        User keyUser = user("u-key", "key-user", List.of("USER"));
        Project project = new Project();
        project.setId("project-1");
        project.setAuthorId("owner-1");
        project.setStatus(ProjectStatus.DRAFT);

        Authentication uploadKey = apiAuthentication(keyUser, "SCOPE_project-1_VERSION_CREATE");
        SecurityContextHolder.getContext().setAuthentication(uploadKey);

        assertTrue(accessControlService.hasProjectPermission(project, keyUser, "VERSION_CREATE"));
    }

    @Test
    void serviceProjectPermissionHonorsCurrentApiKeyOrganizationScope() {
        User keyUser = user("u-key", "key-user", List.of("USER"));
        Project project = new Project();
        project.setId("project-1");
        project.setAuthorId("org-1");
        project.setStatus(ProjectStatus.DRAFT);

        Authentication uploadKey = apiAuthentication(keyUser, "SCOPE_org-1_VERSION_CREATE");
        SecurityContextHolder.getContext().setAuthentication(uploadKey);

        assertTrue(accessControlService.hasProjectPermission(project, keyUser, "VERSION_CREATE"));
    }

    @Test
    void serviceProjectPermissionDoesNotLetApiKeysBorrowUserOwnerAccessWithoutScope() {
        User owner = user("owner-1", "owner", List.of("USER"));
        Project project = new Project();
        project.setId("project-1");
        project.setAuthorId("owner-1");
        project.setStatus(ProjectStatus.DRAFT);

        Authentication unscopedKey = apiAuthentication(owner);
        SecurityContextHolder.getContext().setAuthentication(unscopedKey);

        assertFalse(accessControlService.hasProjectPermission(project, owner, "VERSION_CREATE"));
    }

    @Test
    void serviceProjectPermissionDoesNotApplyApiKeyScopeToDifferentUser() {
        User keyUser = user("u-key", "key-user", List.of("USER"));
        User otherUser = user("u-other", "other-user", List.of("USER"));
        Project project = new Project();
        project.setId("project-1");
        project.setAuthorId("owner-1");
        project.setStatus(ProjectStatus.DRAFT);

        Authentication uploadKey = apiAuthentication(keyUser, "SCOPE_project-1_VERSION_CREATE");
        SecurityContextHolder.getContext().setAuthentication(uploadKey);

        assertFalse(accessControlService.hasProjectPermission(project, otherUser, "VERSION_CREATE"));
    }

    @Test
    void hasCreateProjectPermRequiresApiKeyCreateScope() {
        User currentUser = user("u-1", "Ada", List.of("USER"));
        Authentication personalKey = apiAuthentication(currentUser, "SCOPE_PERSONAL_PROJECT_CREATE");
        when(accountService.getCurrentUser(personalKey)).thenReturn(currentUser);

        assertTrue(accessControlService.hasCreateProjectPerm(null, personalKey));

        Authentication unscopedKey = apiAuthentication(currentUser);
        when(accountService.getCurrentUser(unscopedKey)).thenReturn(currentUser);

        assertFalse(accessControlService.hasCreateProjectPerm(null, unscopedKey));

        User org = organization(
                "org-1",
                "SkyOrg",
                List.of(orgRole("manager", false, Set.of(ApiKey.ApiPermission.PROJECT_CREATE))),
                List.of(orgMember("u-1", "manager", null))
        );
        when(userRepository.findById("org-1")).thenReturn(Optional.of(org));

        Authentication orgKey = apiAuthentication(currentUser, "SCOPE_org-1_PROJECT_CREATE");
        when(accountService.getCurrentUser(orgKey)).thenReturn(currentUser);

        assertTrue(accessControlService.hasCreateProjectPerm("org-1", orgKey));
    }

    @Test
    void hasProjectPermResolvesCanonicalSlugRoutes() {
        Project project = new Project();
        project.setId("project-1");
        project.setSlug("levelingcore");
        project.setStatus(ProjectStatus.PUBLISHED);
        when(projectRepository.findPermissionSnapshotBySlug("levelingcore")).thenReturn(Optional.of(project));

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

        when(mongoTemplate.findOne(any(Query.class), eq(User.class))).thenReturn(org);

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

    private static User userWithAdminPermissions(String id, String username, AdminPermission... permissions) {
        User user = user(id, username, List.of("USER"));
        user.setAdminPermissions(Set.of(permissions));
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

    private static Authentication apiAuthentication(User user, String... scopes) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_API"));
        for (String scope : scopes) {
            authorities.add(new SimpleGrantedAuthority(scope));
        }
        return new UsernamePasswordAuthenticationToken(user, null, authorities);
    }
}
