package net.modtale.config.security;

import java.util.Map;
import java.util.Set;
import net.modtale.config.auth.ApiKeyAuthFilter;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.model.user.User;
import net.modtale.service.auth.AuthenticationService;
import net.modtale.service.auth.LauncherAuthService;
import net.modtale.service.auth.LocalUserDetailsService;
import net.modtale.service.auth.OAuth2LoginService;
import net.modtale.service.auth.OidcLoginService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigLauncherOAuthTest {

    @Test
    void oauthSuccessRedirectsLauncherOAuthToLoopbackCallbackWithCode() throws Exception {
        AccountService accountService = mock(AccountService.class);
        LauncherAuthService launcherAuthService = mock(LauncherAuthService.class);
        SecurityConfig config = config(accountService, launcherAuthService);

        User user = new User();
        user.setId("user-1");
        user.setUsername("ada");
        user.setRoles(java.util.List.of("USER"));
        when(accountService.getPublicProfile("ada")).thenReturn(user);
        when(launcherAuthService.issueCode(user, "http://127.0.0.1:49152/callback", "state-123"))
                .thenReturn(new LauncherAuthService.LauncherAuthGrant(
                        "launcher-code",
                        "http://127.0.0.1:49152/callback",
                        "state-123",
                        300
                ));

        MockHttpServletRequest request = launcherOAuthRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationToken authentication = authentication();
        SecurityContextHolder.getContext().setAuthentication(authentication);

        config.oauthSuccessHandler().onAuthenticationSuccess(request, response, authentication);

        assertEquals(
                "http://127.0.0.1:49152/callback?code=launcher-code&state=state-123",
                response.getRedirectedUrl()
        );
        SecurityContextHolder.clearContext();
    }

    @Test
    void oauthFailureRedirectsLauncherOAuthToLoopbackCallbackWithError() throws Exception {
        SecurityConfig config = config(mock(AccountService.class), mock(LauncherAuthService.class));
        MockHttpServletRequest request = launcherOAuthRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        config.oauthFailureHandler().onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("provider_error"), "Provider failed")
        );

        assertEquals(
                "http://127.0.0.1:49152/callback?error=Provider+failed&state=state-123",
                response.getRedirectedUrl()
        );
    }

    private static SecurityConfig config(AccountService accountService, LauncherAuthService launcherAuthService) {
        return new SecurityConfig(
                mock(ApiKeyAuthFilter.class),
                mock(RateLimitFilter.class),
                mock(OAuth2LoginService.class),
                mock(OidcLoginService.class),
                mock(OAuth2AuthorizedClientRepository.class),
                mock(LocalUserDetailsService.class),
                mock(PasswordEncoder.class),
                accountService,
                mock(AuthenticationService.class),
                launcherAuthService,
                new AppFrontendProperties("http://localhost:5173")
        );
    }

    private static MockHttpServletRequest launcherOAuthRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(
                LauncherAuthService.OAUTH_REDIRECT_URI_SESSION_ATTRIBUTE,
                "http://127.0.0.1:49152/callback"
        );
        request.getSession().setAttribute(
                LauncherAuthService.OAUTH_STATE_SESSION_ATTRIBUTE,
                "state-123"
        );
        return request;
    }

    private static OAuth2AuthenticationToken authentication() {
        DefaultOAuth2User principal = new DefaultOAuth2User(
                Set.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("id", "user-1", "login", "ada"),
                "login"
        );
        return new OAuth2AuthenticationToken(
                principal,
                principal.getAuthorities(),
                "github"
        );
    }
}
