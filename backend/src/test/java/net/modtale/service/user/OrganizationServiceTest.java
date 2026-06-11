package net.modtale.service.user;

import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.InvalidOrganizationRequestException;
import net.modtale.exception.OrganizationOperationForbiddenException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.security.SanitizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationServiceTest {

    private OrganizationService service;
    private UserRepository userRepository;
    private AccessControlService accessControlService;
    private OrganizationApiKeyContextService organizationApiKeyContextService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        TrackingService trackingService = mock(TrackingService.class);
        organizationApiKeyContextService = mock(OrganizationApiKeyContextService.class);
        NotificationService notificationService = mock(NotificationService.class);
        SanitizationService sanitizationService = mock(SanitizationService.class);
        AccountService accountService = mock(AccountService.class);
        accessControlService = mock(AccessControlService.class);
        OrganizationAccessService organizationAccessService = new OrganizationAccessService(userRepository, accessControlService);
        OrganizationConnectionService organizationConnectionService = new OrganizationConnectionService(
                userRepository,
                organizationAccessService,
                new ConnectedAccountMutationService()
        );
        OrganizationRoleService organizationRoleService = mock(OrganizationRoleService.class);
        OrganizationInviteService organizationInviteService = mock(OrganizationInviteService.class);

        service = new OrganizationService(
                userRepository,
                mongoTemplate,
                trackingService,
                organizationApiKeyContextService,
                sanitizationService,
                accountService,
                organizationAccessService,
                organizationConnectionService,
                organizationRoleService,
                organizationInviteService,
                new AppLimitProperties(10, 5, 10, 5, 5, 50, 20, 10)
        );
    }

    @Test
    void createOrganizationRejectsDuplicateNames() {
        when(userRepository.existsByUsernameIgnoreCase("SkyForge")).thenReturn(true);

        assertThrows(
                InvalidOrganizationRequestException.class,
                () -> service.createOrganization("SkyForge", user("user-1"))
        );
    }

    @Test
    void requireConnectionManagedOrganizationRejectsUsersWithoutPermission() {
        User org = new User();
        org.setId("org-1");
        org.setAccountType(User.AccountType.ORGANIZATION);
        org.setOrganizationMembers(List.of(new User.OrganizationMember("user-1", "role-1")));

        when(userRepository.findById("org-1")).thenReturn(Optional.of(org));
        when(accessControlService.hasOrgPermission(org, "user-2", ApiKey.ApiPermission.ORG_CONNECTION_MANAGE)).thenReturn(false);

        assertThrows(
                OrganizationOperationForbiddenException.class,
                () -> service.requireConnectionManagedOrganization("org-1", user("user-2"))
        );
    }

    @Test
    void unlinkOrgAccountRemovesMatchingProviderAndClearsGithubToken() {
        User org = organization("org-1");
        org.setGithubAccessToken("github-token");
        org.setConnectedAccounts(new ArrayList<>(List.of(
                new User.ConnectedAccount(OAuthProvider.GITHUB, "gh-1", "AdaGH", "https://github.com/ada", true)
        )));

        when(userRepository.findById("org-1")).thenReturn(Optional.of(org));
        when(accessControlService.hasOrgPermission(org, "user-1", ApiKey.ApiPermission.ORG_CONNECTION_MANAGE)).thenReturn(true);

        service.unlinkOrgAccount("org-1", "GiThUb", user("user-1"));

        assertTrue(org.getConnectedAccounts().isEmpty());
        assertNull(org.getGithubAccessToken());
        verify(userRepository).save(org);
    }

    @Test
    void toggleOrgConnectionVisibilityMatchesProvidersUsingTheEnumValue() {
        User org = organization("org-1");
        User.ConnectedAccount account = new User.ConnectedAccount(
                OAuthProvider.GITLAB,
                "gl-1",
                "AdaGL",
                "https://gitlab.com/ada",
                false
        );
        org.setConnectedAccounts(new ArrayList<>(List.of(account)));
        org.setGitlabAccessToken("gitlab-token");
        org.setGitlabRefreshToken("refresh-token");
        org.setGitlabTokenExpiresAt(LocalDateTime.now().plusDays(1));

        when(userRepository.findById("org-1")).thenReturn(Optional.of(org));
        when(accessControlService.hasOrgPermission(org, "user-1", ApiKey.ApiPermission.ORG_CONNECTION_MANAGE)).thenReturn(true);

        service.toggleOrgConnectionVisibility("org-1", "gItLaB", user("user-1"));

        assertTrue(account.isVisible());
        verify(userRepository).save(org);
    }

    @Test
    void toggleOrgConnectionVisibilityRejectsGoogleRegardlessOfProviderCase() {
        assertThrows(
                InvalidOrganizationRequestException.class,
                () -> service.toggleOrgConnectionVisibility("org-1", "GOOGLE", user("user-1"))
        );
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user-" + id);
        return user;
    }

    private static User organization(String id) {
        User org = new User();
        org.setId(id);
        org.setUsername("org-" + id);
        org.setAccountType(User.AccountType.ORGANIZATION);
        org.setOrganizationMembers(List.of(new User.OrganizationMember("user-1", "role-1")));
        return org;
    }
}
