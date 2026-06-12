package net.modtale.service.auth;

import java.util.Map;
import java.util.Optional;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.exception.OAuthAccountCollisionException;
import net.modtale.exception.OrganizationNotFoundException;
import net.modtale.model.user.OAuthProvider;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.user.connection.ConnectedAccountMutationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthAccountLinkingServiceTest {

    private UserRepository userRepository;
    private OAuthProviderProfileService providerProfileService;
    private ConnectedAccountMutationService connectedAccountMutationService;
    private OAuthAccountLinkingService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        providerProfileService = mock(OAuthProviderProfileService.class);
        connectedAccountMutationService = mock(ConnectedAccountMutationService.class);
        service = new OAuthAccountLinkingService(userRepository, providerProfileService, connectedAccountMutationService);
    }

    @Test
    void linkAccountRejectsProviderIdAlreadyLinkedToAnotherUser() {
        User currentUser = user("user-1", "willow");
        User otherUser = user("user-2", "ash");
        OAuth2User oauthUser = oauthUser();

        when(providerProfileService.extract("github", oauthUser)).thenReturn(profile(OAuthProvider.GITHUB));
        when(userRepository.findByConnectedAccountsProviderAndProviderId(OAuthProvider.GITHUB, "provider-1"))
                .thenReturn(Optional.of(otherUser));

        assertThrows(
                OAuthAccountCollisionException.class,
                () -> service.linkAccount(currentUser, "github", oauthUser, "token")
        );
    }

    @Test
    void linkAccountLinksProviderAndReturnsLinkingPrincipal() {
        User currentUser = user("user-1", "willow");
        OAuth2User oauthUser = oauthUser();

        when(providerProfileService.extract("github", oauthUser)).thenReturn(profile(OAuthProvider.GITHUB));
        when(userRepository.findByConnectedAccountsProviderAndProviderId(OAuthProvider.GITHUB, "provider-1"))
                .thenReturn(Optional.empty());

        DefaultOAuth2User principal = service.linkAccount(currentUser, "github", oauthUser, "token");

        assertEquals("willow", principal.getAttribute("login"));
        assertEquals("user-1", principal.getAttribute("id"));
        assertEquals(true, principal.getAttribute("is_linking"));
        verify(connectedAccountMutationService).linkProvider(
                currentUser,
                OAuthProvider.GITHUB,
                "provider-1",
                "provider-user",
                "https://example.com/profile",
                true,
                "token"
        );
        verify(userRepository).save(currentUser);
    }

    @Test
    void linkAccountToOrgRejectsPersonalOnlyProvidersAndMissingOrganizations() {
        OAuth2User oauthUser = oauthUser();

        when(providerProfileService.extract("google", oauthUser)).thenReturn(profile(OAuthProvider.GOOGLE));
        when(providerProfileService.extract("github", oauthUser)).thenReturn(profile(OAuthProvider.GITHUB));
        when(userRepository.findById("missing-org")).thenReturn(Optional.empty());

        assertThrows(
                InvalidAuthenticationRequestException.class,
                () -> service.linkAccountToOrg("org-1", "google", oauthUser, "token")
        );
        assertThrows(
                OrganizationNotFoundException.class,
                () -> service.linkAccountToOrg("missing-org", "github", oauthUser, "token")
        );
    }

    @Test
    void linkAccountToOrgAlwaysStoresVisibleConnectionForAllowedProvider() {
        User org = user("org-1", "team");
        OAuth2User oauthUser = oauthUser();

        when(providerProfileService.extract("github", oauthUser)).thenReturn(profile(OAuthProvider.GITHUB));
        when(userRepository.findById("org-1")).thenReturn(Optional.of(org));
        when(userRepository.findByConnectedAccountsProviderAndProviderId(OAuthProvider.GITHUB, "provider-1"))
                .thenReturn(Optional.empty());

        DefaultOAuth2User principal = service.linkAccountToOrg("org-1", "github", oauthUser, "token");

        assertEquals("team", principal.getAttribute("login"));
        verify(connectedAccountMutationService).linkProvider(
                org,
                OAuthProvider.GITHUB,
                "provider-1",
                "provider-user",
                "https://example.com/profile",
                true,
                "token"
        );
        verify(userRepository).save(org);
    }

    private static OAuthProviderProfile profile(OAuthProvider provider) {
        return new OAuthProviderProfile(
                provider,
                "provider-1",
                "provider-user",
                "https://example.com/avatar.png",
                "user@example.com",
                "https://example.com/profile",
                provider != OAuthProvider.GOOGLE
        );
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static OAuth2User oauthUser() {
        return new DefaultOAuth2User(
                java.util.List.of(() -> "ROLE_USER"),
                Map.of("login", "provider-user"),
                "login"
        );
    }
}
