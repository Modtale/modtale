package net.modtale.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import net.modtale.exception.OAuthAccountCollisionException;
import net.modtale.model.user.User;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OidcLoginServiceTest {

    @Test
    void loadUserLinksOrganizationsWhenPendingOrgSessionStateExists() {
        AccountService accountService = mock(AccountService.class);
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        Authentication authentication = mock(Authentication.class);
        request.setUserPrincipal(authentication);
        request.getSession().setAttribute("pending_org_link_id", "org-1");

        when(requestProvider.getIfAvailable()).thenReturn(request);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("ada");

        OidcUser upstreamUser = oidcUser();
        DefaultOAuth2User linkedUser = oauthUser("org-1", "SkyOrg");

        TestOidcLoginService service = new TestOidcLoginService(accountService, authenticationService, requestProvider, upstreamUser);
        when(authenticationService.linkAccountToOrg("org-1", "gitlab", upstreamUser, "access-token")).thenReturn(linkedUser);

        OidcUser result = service.loadUser(oidcRequest("gitlab"));

        assertInstanceOf(OidcLoginService.CustomOidcUser.class, result);
        assertEquals("SkyOrg", result.getAttribute("login"));
        assertNull(request.getSession().getAttribute("pending_org_link_id"));
        verify(authenticationService).linkAccountToOrg("org-1", "gitlab", upstreamUser, "access-token");
        verify(authenticationService, never()).processUserLogin(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void loadUserFallsBackToLoginFlowWhenNoAuthenticatedUserExists() {
        AccountService accountService = mock(AccountService.class);
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);

        OidcUser upstreamUser = oidcUser();
        DefaultOAuth2User appUser = oauthUser("user-1", "Ada");

        TestOidcLoginService service = new TestOidcLoginService(accountService, authenticationService, requestProvider, upstreamUser);
        when(authenticationService.processUserLogin("google", upstreamUser, "access-token")).thenReturn(appUser);

        OidcUser result = service.loadUser(oidcRequest("google"));

        assertInstanceOf(OidcLoginService.CustomOidcUser.class, result);
        assertEquals("Ada", result.getAttribute("login"));
        verify(authenticationService).processUserLogin("google", upstreamUser, "access-token");
        verify(authenticationService, never()).linkAccount(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void loadUserMapsOidcAccountCollisionsToTheExpectedOAuthErrorCode() {
        AccountService accountService = mock(AccountService.class);
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);

        OidcUser upstreamUser = oidcUser();
        TestOidcLoginService service = new TestOidcLoginService(accountService, authenticationService, requestProvider, upstreamUser);
        when(authenticationService.processUserLogin("google", upstreamUser, "access-token"))
                .thenThrow(new OAuthAccountCollisionException("already linked"));

        OAuth2AuthenticationException error = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.loadUser(oidcRequest("google"))
        );

        assertEquals("account_collision", error.getError().getErrorCode());
        assertEquals("already linked", error.getMessage());
    }

    private static OidcUserRequest oidcRequest(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://modtale.test/login/oauth2/code/" + registrationId)
                .authorizationUri("https://example.com/oauth/authorize")
                .tokenUri("https://example.com/oauth/token")
                .jwkSetUri("https://example.com/oauth/keys")
                .issuerUri("https://example.com")
                .userInfoUri("https://example.com/oauth/userinfo")
                .userNameAttributeName("sub")
                .clientName(registrationId)
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                Instant.now(),
                Instant.now().plusSeconds(60)
        );

        OidcIdToken idToken = new OidcIdToken(
                "id-token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("sub", "oidc-123", "email", "ada@example.com")
        );

        return new OidcUserRequest(registration, accessToken, idToken);
    }

    private static OidcUser oidcUser() {
        OidcIdToken idToken = new OidcIdToken(
                "id-token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("sub", "oidc-123", "email", "ada@example.com")
        );
        OidcUserInfo userInfo = new OidcUserInfo(Map.of("sub", "oidc-123", "email", "ada@example.com"));
        return new DefaultOidcUser(Set.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, userInfo, "sub");
    }

    private static DefaultOAuth2User oauthUser(String id, String login) {
        return new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", id, "login", login),
                "login"
        );
    }

    private static final class TestOidcLoginService extends OidcLoginService {
        private final OidcUser upstreamUser;

        private TestOidcLoginService(
                AccountService accountService,
                AuthenticationService authenticationService,
                ObjectProvider<HttpServletRequest> requestProvider,
                OidcUser upstreamUser
        ) {
            super(accountService, authenticationService, requestProvider);
            this.upstreamUser = upstreamUser;
        }

        @Override
        protected OidcUser fetchOidcUser(OidcUserRequest userRequest) {
            return upstreamUser;
        }
    }
}
