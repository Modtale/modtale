package net.modtale.service.auth;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.ApiKeyOperationForbiddenException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.ApiKeyRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.user.organization.OrganizationApiKeyContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    private ApiKeyService service;
    private ApiKeyRepository apiKeyRepository;
    private UserRepository userRepository;
    private ProjectRepository projectRepository;
    private AccessControlService accessControlService;
    private OrganizationApiKeyContextService organizationApiKeyContextService;

    @BeforeEach
    void setUp() {
        apiKeyRepository = mock(ApiKeyRepository.class);
        userRepository = mock(UserRepository.class);
        projectRepository = mock(ProjectRepository.class);
        accessControlService = mock(AccessControlService.class);
        organizationApiKeyContextService = mock(OrganizationApiKeyContextService.class);
        Executor executor = Runnable::run;

        service = new ApiKeyService(
                apiKeyRepository,
                userRepository,
                projectRepository,
                accessControlService,
                organizationApiKeyContextService,
                executor,
                new AppLimitProperties(10, 5, 10, 5, 5, 50, 20, 10)
        );
    }

    @Test
    void createApiKeyResolvesOrganizationContextsViaSharedHelperAndTrimsPermissionsToTheMemberRole() {
        User user = new User();
        user.setId("user-1");
        user.setTier(ApiKey.Tier.ENTERPRISE);

        User.OrganizationRole memberRole = new User.OrganizationRole(
                "role-1",
                "Member",
                "#3b82f6",
                EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ, ApiKey.ApiPermission.VERSION_READ)
        );
        User org = new User();
        org.setId("org-1");
        org.setAccountType(User.AccountType.ORGANIZATION);
        org.setOrganizationRoles(List.of(memberRole));
        org.setOrganizationMembers(List.of(new User.OrganizationMember("user-1", "role-1")));

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(apiKeyRepository.findByUserId("user-1")).thenReturn(List.of());
        when(organizationApiKeyContextService.getUserOrganizations("user-1")).thenReturn(List.of(org));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String plainKey = service.createApiKey(
                "user-1",
                "CI key",
                Map.of("org-1", EnumSet.of(
                        ApiKey.ApiPermission.ORG_MEMBER_READ,
                        ApiKey.ApiPermission.ORG_DELETE
                ))
        );

        assertNotNull(plainKey);

        ArgumentCaptor<ApiKey> apiKeyCaptor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(apiKeyCaptor.capture());

        ApiKey savedKey = apiKeyCaptor.getValue();
        assertEquals(ApiKey.Tier.ENTERPRISE, savedKey.getTier());
        assertEquals(
                EnumSet.of(ApiKey.ApiPermission.ORG_MEMBER_READ),
                savedKey.getContextPermissions().get("org-1")
        );
    }

    @Test
    void createApiKeyRejectsProjectContextsForUsersWhoAreNotContributors() {
        User user = new User();
        user.setId("user-1");
        user.setTier(ApiKey.Tier.USER);

        net.modtale.model.project.Project project = new net.modtale.model.project.Project();
        project.setId("project-1");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(apiKeyRepository.findByUserId("user-1")).thenReturn(List.of());
        when(organizationApiKeyContextService.getUserOrganizations("user-1")).thenReturn(List.of());
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(accessControlService.isOwner(project, user)).thenReturn(false);

        assertThrows(
                ApiKeyOperationForbiddenException.class,
                () -> service.createApiKey(
                        "user-1",
                        "project key",
                        Map.of("project-1", EnumSet.of(ApiKey.ApiPermission.PROJECT_EDIT_METADATA))
                )
        );
    }

    @Test
    void revokeKeyRejectsUsersWhoDoNotOwnTheKey() {
        ApiKey key = new ApiKey("other-user", "CI key", "hash", "md_prefix");
        key.setId("key-1");

        when(apiKeyRepository.findById("key-1")).thenReturn(Optional.of(key));

        assertThrows(
                ApiKeyOperationForbiddenException.class,
                () -> service.revokeKey("key-1", "user-1")
        );
    }

    @Test
    void revokeKeyThrowsWhenTheKeyDoesNotExist() {
        when(apiKeyRepository.findById("missing-key")).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.revokeKey("missing-key", "user-1")
        );
    }
}
