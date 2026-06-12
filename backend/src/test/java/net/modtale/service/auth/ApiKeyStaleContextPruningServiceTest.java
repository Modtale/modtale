package net.modtale.service.auth;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.access.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyStaleContextPruningServiceTest {

    private ApiKeyRepository apiKeyRepository;
    private UserRepository userRepository;
    private ProjectRepository projectRepository;
    private AccessControlService accessControlService;
    private ApiKeyStaleContextPruningService service;

    @BeforeEach
    void setUp() {
        apiKeyRepository = mock(ApiKeyRepository.class);
        userRepository = mock(UserRepository.class);
        projectRepository = mock(ProjectRepository.class);
        accessControlService = mock(AccessControlService.class);
        service = new ApiKeyStaleContextPruningService(
                apiKeyRepository,
                userRepository,
                projectRepository,
                new ApiKeyContextPermissionService(accessControlService),
                Runnable::run
        );
    }

    @Test
    void pruneInvalidContextsLeavesUnscopedKeysAlone() {
        ApiKey key = new ApiKey("user-1", "unscoped", "hash", "md_prefix");

        service.pruneInvalidContexts(key);

        verify(apiKeyRepository, never()).save(key);
    }

    @Test
    void pruneInvalidContextsTrimsOrganizationPermissionsAndRemovesDeletedProjects() {
        ApiKey key = key("user-1", mutableContexts(Map.of(
                "PERSONAL", EnumSet.of(ApiKey.ApiPermission.PROFILE_READ),
                "org-1", EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ, ApiKey.ApiPermission.ORG_DELETE),
                "deleted-project", EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE)
        )));
        User owner = user("user-1");
        User organization = organization("org-1", "user-1");
        Project deletedProject = new Project();
        deletedProject.setId("deleted-project");
        deletedProject.setStatus(ProjectStatus.DELETED);

        when(userRepository.findById("user-1")).thenReturn(Optional.of(owner));
        when(userRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(userRepository.findById("deleted-project")).thenReturn(Optional.empty());
        when(projectRepository.findById("deleted-project")).thenReturn(Optional.of(deletedProject));

        service.pruneInvalidContexts(key);

        assertEquals(EnumSet.of(ApiKey.ApiPermission.PROFILE_READ), key.getContextPermissions().get("PERSONAL"));
        assertEquals(EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ), key.getContextPermissions().get("org-1"));
        assertFalse(key.getContextPermissions().containsKey("deleted-project"));
        verify(apiKeyRepository).save(key);
    }

    @Test
    void pruneInvalidContextsTrimsProjectPermissionsToCurrentRole() {
        ApiKey key = key("user-1", mutableContexts(Map.of(
                "project-1", EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE, ApiKey.ApiPermission.PROJECT_DELETE)
        )));
        User owner = user("user-1");
        Project project = project("project-1", "user-1");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(owner));
        when(userRepository.findById("project-1")).thenReturn(Optional.empty());
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(accessControlService.isOwner(project, owner)).thenReturn(false);

        service.pruneInvalidContexts(key);

        assertEquals(EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE), key.getContextPermissions().get("project-1"));
        verify(apiKeyRepository).save(key);
    }

    @Test
    void pruneInvalidContextsDoesNotSaveWhenPermissionsAreAlreadyValid() {
        ApiKey key = key("user-1", mutableContexts(Map.of(
                "org-1", EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ)
        )));
        User organization = organization("org-1", "user-1");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user("user-1")));
        when(userRepository.findById("org-1")).thenReturn(Optional.of(organization));

        service.pruneInvalidContexts(key);

        verify(apiKeyRepository, never()).save(key);
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
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setProjectRoles(List.of(new Project.ProjectRole(
                "role-1",
                "Uploader",
                "#22c55e",
                EnumSet.of(ApiKey.ApiPermission.VERSION_CREATE)
        )));
        project.setTeamMembers(List.of(new Project.ProjectMember(memberUserId, "role-1")));
        return project;
    }

    private static ApiKey key(String userId, Map<String, Set<ApiKey.ApiPermission>> contexts) {
        ApiKey key = new ApiKey(userId, "CI key", "hash", "md_prefix");
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
