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
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2LoginServiceTest {

    @Test
    void loadUserLinksAccountsForSignedInUsers() {
        AccountService accountService = mock(AccountService.class);
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        Authentication authentication = mock(Authentication.class);

        when(requestProvider.getIfAvailable()).thenReturn(request);
        when(request.getUserPrincipal()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("ada");

        User currentUser = new User();
        currentUser.setId("user-1");
        currentUser.setUsername("Ada");
        when(accountService.getCurrentUser()).thenReturn(currentUser);

        DefaultOAuth2User upstreamUser = oauthUser("oauth-1", "ada-gh");
        DefaultOAuth2User linkedUser = oauthUser("user-1", "Ada");

        TestOAuth2LoginService service = new TestOAuth2LoginService(accountService, authenticationService, requestProvider, upstreamUser);
        when(authenticationService.linkAccount(currentUser, "github", upstreamUser, "access-token")).thenReturn(linkedUser);

        OAuth2User result = service.loadUser(oauthRequest("github"));

        assertSame(linkedUser, result);
        verify(authenticationService).linkAccount(currentUser, "github", upstreamUser, "access-token");
        verify(authenticationService, never()).processUserLogin(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void loadUserFallsBackToLoginFlowForAnonymousRequests() {
        AccountService accountService = mock(AccountService.class);
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);

        DefaultOAuth2User upstreamUser = oauthUser("oauth-1", "ada-gh");
        DefaultOAuth2User signedInUser = oauthUser("user-1", "Ada");

        TestOAuth2LoginService service = new TestOAuth2LoginService(accountService, authenticationService, requestProvider, upstreamUser);
        when(authenticationService.processUserLogin("github", upstreamUser, "access-token")).thenReturn(signedInUser);

        OAuth2User result = service.loadUser(oauthRequest("github"));

        assertSame(signedInUser, result);
        verify(authenticationService).processUserLogin("github", upstreamUser, "access-token");
        verify(authenticationService, never()).linkAccount(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void loadUserRejectsGitlabAsSignInProvider() {
        AccountService accountService = mock(AccountService.class);
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);

        DefaultOAuth2User upstreamUser = oauthUser("oauth-1", "ada-gitlab");
        TestOAuth2LoginService service = new TestOAuth2LoginService(accountService, authenticationService, requestProvider, upstreamUser);

        OAuth2AuthenticationException error = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.loadUser(oauthRequest("gitlab"))
        );

        assertEquals("login_failure", error.getError().getErrorCode());
        verify(authenticationService, never()).processUserLogin(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void loadUserUsesLoginFlowForLauncherOAuthEvenWithExistingBrowserSession() {
        AccountService accountService = mock(AccountService.class);
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        Authentication authentication = mock(Authentication.class);
        request.setUserPrincipal(authentication);
        request.getSession().setAttribute(
                LauncherAuthService.OAUTH_REDIRECT_URI_SESSION_ATTRIBUTE,
                "http://127.0.0.1:49152/callback"
        );

        when(requestProvider.getIfAvailable()).thenReturn(request);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("ada");

        DefaultOAuth2User upstreamUser = oauthUser("oauth-1", "ada-gh");
        DefaultOAuth2User signedInUser = oauthUser("user-1", "Ada");

        TestOAuth2LoginService service = new TestOAuth2LoginService(accountService, authenticationService, requestProvider, upstreamUser);
        when(authenticationService.processUserLogin("github", upstreamUser, "access-token")).thenReturn(signedInUser);

        OAuth2User result = service.loadUser(oauthRequest("github"));

        assertSame(signedInUser, result);
        verify(authenticationService).processUserLogin("github", upstreamUser, "access-token");
        verify(authenticationService, never()).linkAccount(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void loadUserMapsAccountCollisionsToTheExpectedOAuthErrorCode() {
        AccountService accountService = mock(AccountService.class);
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        ObjectProvider<HttpServletRequest> requestProvider = mock(ObjectProvider.class);

        DefaultOAuth2User upstreamUser = oauthUser("oauth-1", "ada-gh");
        TestOAuth2LoginService service = new TestOAuth2LoginService(accountService, authenticationService, requestProvider, upstreamUser);
        when(authenticationService.processUserLogin("github", upstreamUser, "access-token"))
                .thenThrow(new OAuthAccountCollisionException("linked elsewhere"));

        OAuth2AuthenticationException error = assertThrows(
                OAuth2AuthenticationException.class,
                () -> service.loadUser(oauthRequest("github"))
        );

        assertEquals("account_collision", error.getError().getErrorCode());
        assertEquals("linked elsewhere", error.getMessage());
    }

    private static OAuth2UserRequest oauthRequest(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://modtale.test/login/oauth2/code/" + registrationId)
                .authorizationUri("https://example.com/oauth/authorize")
                .tokenUri("https://example.com/oauth/token")
                .userInfoUri("https://example.com/oauth/userinfo")
                .userNameAttributeName("id")
                .clientName(registrationId)
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                Instant.now(),
                Instant.now().plusSeconds(60)
        );

        return new OAuth2UserRequest(registration, accessToken);
    }

    private static DefaultOAuth2User oauthUser(String id, String login) {
        return new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", id, "login", login),
                "login"
        );
    }

    private static final class TestOAuth2LoginService extends OAuth2LoginService {
        private final OAuth2User upstreamUser;

        private TestOAuth2LoginService(
                AccountService accountService,
                AuthenticationService authenticationService,
                ObjectProvider<HttpServletRequest> requestProvider,
                OAuth2User upstreamUser
        ) {
            super(accountService, authenticationService, requestProvider);
            this.upstreamUser = upstreamUser;
        }

        @Override
        protected OAuth2User fetchOAuthUser(OAuth2UserRequest userRequest) {
            return upstreamUser;
        }
    }
}
