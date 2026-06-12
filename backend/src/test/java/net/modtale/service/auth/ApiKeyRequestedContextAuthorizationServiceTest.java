package net.modtale.service.auth;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.modtale.exception.ApiKeyOperationForbiddenException;
import net.modtale.exception.ProjectNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.user.organization.OrganizationApiKeyContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyRequestedContextAuthorizationServiceTest {

    private ApiKeyRepository apiKeyRepository;
    private ProjectRepository projectRepository;
    private OrganizationApiKeyContextService organizationApiKeyContextService;
    private AccessControlService accessControlService;
    private ApiKeyRequestedContextAuthorizationService service;

    @BeforeEach
    void setUp() {
        apiKeyRepository = mock(ApiKeyRepository.class);
        projectRepository = mock(ProjectRepository.class);
        organizationApiKeyContextService = mock(OrganizationApiKeyContextService.class);
        accessControlService = mock(AccessControlService.class);
        service = new ApiKeyRequestedContextAuthorizationService(
                apiKeyRepository,
                projectRepository,
                organizationApiKeyContextService,
                new ApiKeyContextPermissionService(accessControlService)
        );
    }

    @Test
    void validateRequestedContextsKeepsPersonalAndTrimsOrganizationAndProjectPermissions() {
        User user = user("user-1");
        User organization = organization("org-1", "user-1");
        Project project = project("project-1", "user-1");

        when(organizationApiKeyContextService.getUserOrganizations("user-1")).thenReturn(List.of(organization));
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(accessControlService.isOwner(project, user)).thenReturn(false);

        Map<String, Set<ApiKey.ApiPermission>> validated = service.validateRequestedContexts(
                user,
                Map.of(
                        "PERSONAL", EnumSet.of(ApiKey.ApiPermission.PROFILE_READ),
                        "org-1", EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ, ApiKey.ApiPermission.ORG_DELETE),
                        "project-1", EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE, ApiKey.ApiPermission.PROJECT_DELETE)
                )
        );

        assertEquals(EnumSet.of(ApiKey.ApiPermission.PROFILE_READ), validated.get("PERSONAL"));
        assertEquals(EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ), validated.get("org-1"));
        assertEquals(EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE), validated.get("project-1"));
    }

    @Test
    void validateRequestedContextsRejectsOrganizationsWithoutCurrentMembership() {
        User user = user("user-1");
        User organization = organization("org-1", "someone-else");

        when(organizationApiKeyContextService.getUserOrganizations("user-1")).thenReturn(List.of(organization));

        assertThrows(
                ApiKeyOperationForbiddenException.class,
                () -> service.validateRequestedContexts(
                        user,
                        Map.of("org-1", EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ))
                )
        );
    }

    @Test
    void validateRequestedContextsRejectsUnknownProjectContext() {
        User user = user("user-1");

        when(organizationApiKeyContextService.getUserOrganizations("user-1")).thenReturn(List.of());
        when(projectRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(
                ProjectNotFoundException.class,
                () -> service.validateRequestedContexts(
                        user,
                        Map.of("missing", EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE))
                )
        );
    }

    @Test
    void syncUserProjectPermissionsTrimsRemovesAndSkipsUnchangedKeys() {
        ApiKey needsTrim = key("key-1", "user-1", mutableContexts(Map.of(
                "project-1", EnumSet.of(ApiKey.ApiPermission.VERSION_READ, ApiKey.ApiPermission.VERSION_DELETE)
        )));
        ApiKey needsRemoval = key("key-2", "user-1", mutableContexts(Map.of(
                "project-1", EnumSet.of(ApiKey.ApiPermission.PROJECT_DELETE)
        )));
        ApiKey unchanged = key("key-3", "user-1", mutableContexts(Map.of(
                "project-1", EnumSet.of(ApiKey.ApiPermission.VERSION_READ)
        )));
        ApiKey noContext = key("key-4", "user-1", mutableContexts(Map.of(
                "PERSONAL", EnumSet.of(ApiKey.ApiPermission.PROFILE_READ)
        )));

        when(apiKeyRepository.findByUserId("user-1")).thenReturn(List.of(needsTrim, needsRemoval, unchanged, noContext));

        service.syncUserProjectPermissions(
                "user-1",
                "project-1",
                EnumSet.of(ApiKey.ApiPermission.VERSION_READ)
        );

        assertEquals(EnumSet.of(ApiKey.ApiPermission.VERSION_READ), needsTrim.getContextPermissions().get("project-1"));
        assertTrue(needsRemoval.getContextPermissions().isEmpty());
        assertEquals(EnumSet.of(ApiKey.ApiPermission.VERSION_READ), unchanged.getContextPermissions().get("project-1"));

        verify(apiKeyRepository).save(needsTrim);
        verify(apiKeyRepository).save(needsRemoval);
        verify(apiKeyRepository, never()).save(unchanged);
        verify(apiKeyRepository, never()).save(noContext);
    }

    @Test
    void syncUserProjectPermissionsRemovesEveryContextWhenAllowedSetIsEmpty() {
        ApiKey key = key("key-1", "user-1", mutableContexts(Map.of(
                "project-1", EnumSet.of(ApiKey.ApiPermission.VERSION_READ)
        )));
        when(apiKeyRepository.findByUserId("user-1")).thenReturn(List.of(key));

        service.syncUserProjectPermissions("user-1", "project-1", Set.of());

        assertTrue(key.getContextPermissions().isEmpty());
        verify(apiKeyRepository).save(key);
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static User organization(String id, String memberUserId) {
        User.OrganizationRole role = new User.OrganizationRole(
                "role-1",
                "Viewer",
                "#0ea5e9",
                EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ)
        );
        User organization = new User();
        organization.setId(id);
        organization.setAccountType(User.AccountType.ORGANIZATION);
        organization.setOrganizationRoles(List.of(role));
        organization.setOrganizationMembers(List.of(new User.OrganizationMember(memberUserId, "role-1")));
        return organization;
    }

    private static Project project(String id, String memberUserId) {
        Project project = new Project();
        project.setId(id);
        project.setProjectRoles(List.of(new Project.ProjectRole(
                "role-1",
                "Uploader",
                "#22c55e",
                EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE)
        )));
        project.setTeamMembers(List.of(new Project.ProjectMember(memberUserId, "role-1")));
        return project;
    }

    private static ApiKey key(String id, String userId, Map<String, Set<ApiKey.ApiPermission>> contexts) {
        ApiKey key = new ApiKey(userId, id, "hash", "md_prefix");
        key.setId(id);
        key.setContextPermissions(contexts);
        return key;
    }

    private static Map<String, Set<ApiKey.ApiPermission>> mutableContexts(
            Map<String, Set<ApiKey.ApiPermission>> contexts
    ) {
        Map<String, Set<ApiKey.ApiPermission>> mutable = new HashMap<>();
        contexts.forEach((context, permissions) -> mutable.put(context, EnumSet.copyOf(permissions)));
        return mutable;
    }
}
