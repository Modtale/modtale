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
