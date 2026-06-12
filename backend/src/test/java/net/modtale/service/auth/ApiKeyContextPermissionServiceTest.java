package net.modtale.service.auth;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.modtale.exception.ApiKeyOperationForbiddenException;
import net.modtale.model.project.Project;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.security.access.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiKeyContextPermissionServiceTest {

    private AccessControlService accessControlService;
    private ApiKeyContextPermissionService service;

    @BeforeEach
    void setUp() {
        accessControlService = mock(AccessControlService.class);
        service = new ApiKeyContextPermissionService(accessControlService);
    }

    @Test
    void organizationAdminMembershipReceivesAllRequestedPermissions() {
        User.OrganizationMember membership = new User.OrganizationMember("user-1", null);
        membership.setRole("ADMIN");

        Set<ApiKey.ApiPermission> allowed = service.filterOrganizationPermissions(
                organization("org-1"),
                membership,
                EnumSet.of(ApiKey.ApiPermission.ORG_DELETE, ApiKey.ApiPermission.ORG_MEMBER_READ)
        );

        assertEquals(EnumSet.of(ApiKey.ApiPermission.ORG_DELETE, ApiKey.ApiPermission.ORG_MEMBER_READ), allowed);
    }

    @Test
    void organizationRoleIntersectsRequestedPermissions() {
        User.OrganizationRole role = new User.OrganizationRole(
                "role-1",
                "Maintainer",
                "#38bdf8",
                EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ, ApiKey.ApiPermission.VERSION_READ)
        );
        User organization = organization("org-1");
        organization.setOrganizationRoles(List.of(role));

        Set<ApiKey.ApiPermission> allowed = service.filterOrganizationPermissions(
                organization,
                new User.OrganizationMember("user-1", "role-1"),
                EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ, ApiKey.ApiPermission.ORG_DELETE)
        );

        assertEquals(EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ), allowed);
    }

    @Test
    void organizationOwnerRoleReceivesAllRequestedPermissions() {
        User.OrganizationRole role = new User.OrganizationRole("owner-role", "Owner", "#f59e0b", Set.of());
        role.setOwner(true);
        User organization = organization("org-1");
        organization.setOrganizationRoles(List.of(role));

        Set<ApiKey.ApiPermission> allowed = service.filterOrganizationPermissions(
                organization,
                new User.OrganizationMember("user-1", "owner-role"),
                EnumSet.of(ApiKey.ApiPermission.ORG_DELETE, ApiKey.ApiPermission.ORG_CONNECTION_MANAGE)
        );

        assertEquals(EnumSet.of(ApiKey.ApiPermission.ORG_DELETE, ApiKey.ApiPermission.ORG_CONNECTION_MANAGE), allowed);
    }

    @Test
    void missingOrganizationRoleOrPermissionsYieldsNoPermissions() {
        User organization = organization("org-1");
        organization.setOrganizationRoles(List.of(new User.OrganizationRole("viewer", "Viewer", "#fff", null)));

        Set<ApiKey.ApiPermission> missingRoleAllowed = service.filterOrganizationPermissions(
                organization,
                new User.OrganizationMember("user-1", "missing"),
                EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ)
        );
        Set<ApiKey.ApiPermission> roleWithNoPermissionsAllowed = service.filterOrganizationPermissions(
                organization,
                new User.OrganizationMember("user-1", "viewer"),
                EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ)
        );

        assertTrue(missingRoleAllowed.isEmpty());
        assertTrue(roleWithNoPermissionsAllowed.isEmpty());
    }

    @Test
    void projectOwnerReceivesAllRequestedPermissions() {
        Project project = project("project-1");
        User user = user("user-1");

        when(accessControlService.isOwner(project, user)).thenReturn(true);

        Set<ApiKey.ApiPermission> allowed = service.filterProjectPermissions(
                project,
                user,
                "user-1",
                EnumSet.of(ApiKey.ApiPermission.PROJECT_DELETE, ApiKey.ApiPermission.VERSION_CREATE)
        );

        assertEquals(EnumSet.of(ApiKey.ApiPermission.PROJECT_DELETE, ApiKey.ApiPermission.VERSION_CREATE), allowed);
    }

    @Test
    void projectMemberPermissionsAreTrimmedToTheirRole() {
        Project project = project("project-1");
        project.setTeamMembers(List.of(new Project.ProjectMember("user-1", "role-1")));
        project.setProjectRoles(List.of(new Project.ProjectRole(
                "role-1",
                "Uploader",
                "#22c55e",
                EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE, ApiKey.ApiPermission.VERSION_READ)
        )));
        User user = user("user-1");

        when(accessControlService.isOwner(project, user)).thenReturn(false);

        Set<ApiKey.ApiPermission> allowed = service.filterProjectPermissions(
                project,
                user,
                "user-1",
                EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE, ApiKey.ApiPermission.PROJECT_DELETE)
        );

        assertEquals(EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE), allowed);
    }

    @Test
    void missingProjectMembershipThrowsOrReturnsEmptyDependingOnMode() {
        Project project = project("project-1");
        User user = user("user-1");

        when(accessControlService.isOwner(project, user)).thenReturn(false);

        assertThrows(
                ApiKeyOperationForbiddenException.class,
                () -> service.filterProjectPermissions(
                        project,
                        user,
                        "user-1",
                        EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE)
                )
        );
        assertTrue(service.filterProjectPermissionsOrEmpty(
                project,
                user,
                "user-1",
                EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE)
        ).isEmpty());
    }

    @Test
    void activeOrganizationRequiresOrganizationAccountThatIsNotDeleted() {
        User organization = organization("org-1");
        User userAccount = user("user-1");
        User deletedOrganization = organization("org-2");
        deletedOrganization.setDeletedAt(LocalDateTime.now());

        assertTrue(service.isActiveOrganization(organization));
        assertFalse(service.isActiveOrganization(userAccount));
        assertFalse(service.isActiveOrganization(deletedOrganization));
        assertFalse(service.isActiveOrganization(null));
    }

    @Test
    void findOrganizationMembershipHandlesMissingMembers() {
        User organization = organization("org-1");
        organization.setOrganizationMembers(List.of(new User.OrganizationMember("user-1", "role-1")));

        assertEquals("role-1", service.findOrganizationMembership(organization, "user-1").getRoleId());
        assertNull(service.findOrganizationMembership(organization, "missing"));

        organization.setOrganizationMembers(null);
        assertNull(service.findOrganizationMembership(organization, "user-1"));
    }

    private static User organization(String id) {
        User organization = new User();
        organization.setId(id);
        organization.setAccountType(User.AccountType.ORGANIZATION);
        return organization;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static Project project(String id) {
        Project project = new Project();
        project.setId(id);
        return project;
    }
}
