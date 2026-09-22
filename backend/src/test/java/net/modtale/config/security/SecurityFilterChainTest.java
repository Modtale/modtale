package net.modtale.config.security;

import net.modtale.config.auth.ApiKeyAuthFilter;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.service.auth.*;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SecurityFilterChainTest {
    @Test
    void realChainKeepsCsrfAndHasNoLegacyPasswordLoginFilter() {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestSecurity.class);
            context.refresh();
            var filters = context.getBean(SecurityFilterChain.class).getFilters();
            assertTrue(filters.stream().anyMatch(CsrfFilter.class::isInstance));
            assertFalse(filters.stream().anyMatch(UsernamePasswordAuthenticationFilter.class::isInstance));
        }
    }

    @Test
    void localhostTextInsideRemoteFrontendDoesNotDisableSecureCookies() {
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        configuration("https://localhost.attacker.test").cookieSerializer().writeCookieValue(
                new org.springframework.session.web.http.CookieSerializer.CookieValue(request, response, "session"));
        assertTrue(response.getHeader("Set-Cookie").contains("Secure"));
    }

    @Test
    void enrollmentAuthorizationRejectsApiKeysAndRequiresBrowserCsrf() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestSecurity.class); context.refresh();
            var filters = context.getBean(SecurityFilterChain.class).getFilters();
            var authorization = filters.stream().filter(org.springframework.security.web.access.intercept.AuthorizationFilter.class::isInstance)
                    .map(org.springframework.security.web.access.intercept.AuthorizationFilter.class::cast).findFirst().orElseThrow();
            var csrf = filters.stream().filter(CsrfFilter.class::isInstance).map(CsrfFilter.class::cast).findFirst().orElseThrow();
            for (String path : new String[]{"/api/v1/auth/mfa/setup", "/api/v1/auth/mfa/verify"}) {
                for (boolean apiKey : new boolean[]{false, true}) {
                    for (boolean token : new boolean[]{false, true}) {
                        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", path);
                        request.setServletPath(path);
                        var response = new org.springframework.mock.web.MockHttpServletResponse();
                        var reached = new java.util.concurrent.atomic.AtomicBoolean();
                        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("user", null,
                                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(apiKey ? "ROLE_API" : "ROLE_USER")));
                        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                        if (apiKey) request.addHeader("X-MODTALE-KEY", "fixture-key");
                        if (token) {
                            request.setCookies(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "fixture-csrf"));
                            request.addHeader("X-XSRF-TOKEN", "fixture-csrf");
                        }
                        try {
                            if (apiKey) {
                                assertThrows(org.springframework.security.access.AccessDeniedException.class,
                                        () -> csrf.doFilter(request, response, (req, res) -> authorization.doFilter(req, res, (r, s) -> reached.set(true))));
                            } else {
                                csrf.doFilter(request, response, (req, res) -> authorization.doFilter(req, res, (r, s) -> reached.set(true)));
                                assertEquals(token, reached.get());
                                if (!token) assertEquals(403, response.getStatus());
                            }
                            if (apiKey) assertFalse(reached.get());
                        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
                    }
                }
            }
        }
    }

    @Configuration
    @EnableWebSecurity
    static class TestSecurity {
        @Bean
        ClientRegistrationRepository clients() {
            return mock(ClientRegistrationRepository.class);
        }

        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            var configuration = configuration("https://preview-example.run.app");
            return configuration.securityFilterChain(http, mock(OAuth2AuthorizationRequestResolver.class));
        }
    }

    private static SecurityConfig configuration(String frontendUrl) {
        return new SecurityConfig(
                    mock(ApiKeyAuthFilter.class), mock(RateLimitFilter.class),
                    mock(OAuth2LoginService.class), mock(OidcLoginService.class),
                    mock(OAuth2AuthorizedClientRepository.class), mock(LocalUserDetailsService.class),
                    mock(PasswordEncoder.class), mock(AccountService.class),
                    mock(AuthenticationService.class), mock(LauncherAuthService.class),
                    new AppFrontendProperties(frontendUrl)
            );
    }
}
