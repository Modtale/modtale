package net.modtale.controller.auth;

import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.ConcurrentHashMap;
import net.modtale.config.auth.ApiKeyAuthFilter;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.config.security.RateLimitFilter;
import net.modtale.config.security.SecurityConfig;
import net.modtale.model.user.User;
import net.modtale.service.auth.*;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PreviewSessionFlowTest {
    private static final String FRONTEND = "https://frontend-preview.run.app";
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class Config {
        @Bean AuthenticationService authentication() { return mock(AuthenticationService.class); }
        @Bean LauncherAuthService launcher() { return mock(LauncherAuthService.class); }
        @Bean AccountService accounts() { return mock(AccountService.class); }
        @Bean ClientRegistrationRepository clients() { return mock(ClientRegistrationRepository.class); }
        @Bean SecurityConfig securityConfig(AuthenticationService auth, LauncherAuthService launcher, AccountService accounts) {
            var keys = mock(ApiKeyService.class);
            return new SecurityConfig(
                    new ApiKeyAuthFilter(keys, (req, res, handler, ex) -> {
                        res.setStatus(401);
                        return new org.springframework.web.servlet.ModelAndView();
                    }),
                    new RateLimitFilter(keys), mock(OAuth2LoginService.class), mock(OidcLoginService.class),
                    mock(OAuth2AuthorizedClientRepository.class), mock(LocalUserDetailsService.class),
                    mock(PasswordEncoder.class), accounts, auth, launcher, new AppFrontendProperties(FRONTEND));
        }
        @Bean SecurityFilterChain chain(HttpSecurity http, SecurityConfig config) throws Exception {
            return config.securityFilterChain(http, mock(OAuth2AuthorizationRequestResolver.class));
        }
        @Bean AuthController controller(AuthenticationService auth, LauncherAuthService launcher,
                AccountService accounts, SecurityConfig config) {
            return new AuthController(auth, mock(AuthenticationMutationService.class), accounts,
                    mock(TwoFactorService.class), launcher, config.securityContextRepository(),
                    mock(MfaEnrollmentService.class), config.csrfTokenRepository());
        }
        @Bean FixtureController fixtureController() { return new FixtureController(); }
    }

    @RestController
    static class FixtureController {
        @GetMapping("/api/v1/fixture/me")
        String currentUser(Authentication authentication) {
            return ((User) authentication.getPrincipal()).getUsername();
        }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        var sessions = new MapSessionRepository(new ConcurrentHashMap<>());
        var sessionFilter = new SessionRepositoryFilter<MapSession>(sessions);
        var resolver = new CookieHttpSessionIdResolver();
        resolver.setCookieSerializer(context.getBean(SecurityConfig.class).cookieSerializer());
        sessionFilter.setHttpSessionIdResolver(resolver);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(sessionFilter, context.getBean("springSecurityFilterChain", Filter.class)).build();
        User fixture = new User();
        fixture.setId("fixture-user");
        fixture.setUsername("fixture");
        fixture.setRoles(java.util.List.of("USER"));
        when(context.getBean(AuthenticationService.class).authenticate("fixture", "fixture-password")).thenReturn(fixture);
    }

    @AfterEach
    void cleanup() {
        context.close();
        SecurityContextHolder.clearContext();
    }

    @Test
    void freshCredentialedRequestsRestoreSessionAndLogoutExpiresPartitionedCookies() throws Exception {
        var login = signIn();
        var session = responseCookie(login, "SESSION");
        var csrf = responseCookie(login, "XSRF-TOKEN");
        mvc.perform(get("/api/v1/fixture/me").cookie(session, csrf)
                        .header("User-Agent", "Mozilla/5.0").header("Origin", FRONTEND))
                .andExpect(status().isOk()).andExpect(content().string("fixture"));

        mvc.perform(post("/api/v1/auth/logout").cookie(session, csrf)
                        .header("User-Agent", "Mozilla/5.0").header("Origin", FRONTEND))
                .andExpect(status().isForbidden());
        // A rejected logout must not destroy the authenticated session.
        mvc.perform(get("/api/v1/fixture/me").cookie(session, csrf)
                        .header("User-Agent", "Mozilla/5.0").header("Origin", FRONTEND))
                .andExpect(status().isOk());

        var logout = mvc.perform(post("/api/v1/auth/logout").cookie(session, csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .header("User-Agent", "Mozilla/5.0").header("Origin", FRONTEND))
                .andExpect(status().isOk()).andReturn();
        for (String name : new String[]{"SESSION", "XSRF-TOKEN"}) {
            assertTrue(logout.getResponse().getHeaders("Set-Cookie").stream().anyMatch(header ->
                    header.startsWith(name + "=") && header.contains("Max-Age=0")
                            && header.contains("Secure") && header.contains("SameSite=None")
                            && header.contains("Partitioned")), logout.getResponse().getHeaders("Set-Cookie").toString());
        }
        mvc.perform(get("/api/v1/fixture/me").cookie(session, csrf)
                        .header("User-Agent", "Mozilla/5.0").header("Origin", FRONTEND))
                .andExpect(status().isUnauthorized());

        // Sign-in after logout creates a new authenticated session.
        var nextLogin = signIn();
        assertNotEquals(session.getValue(), responseCookie(nextLogin, "SESSION").getValue());
        mvc.perform(get("/api/v1/fixture/me").cookie(responseCookie(nextLogin, "SESSION"))
                        .header("User-Agent", "Mozilla/5.0").header("Origin", FRONTEND))
                .andExpect(status().isOk()).andExpect(content().string("fixture"));
    }

    private MvcResult signIn() throws Exception {
        return mvc.perform(post("/api/v1/auth/signin")
                        .header("User-Agent", "Mozilla/5.0").header("Origin", FRONTEND)
                        .contentType("application/json")
                        .content("{\"username\":\"fixture\",\"password\":\"fixture-password\"}"))
                .andExpect(status().isOk()).andReturn();
    }

    private static Cookie responseCookie(MvcResult result, String name) {
        String header = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith(name + "=")).findFirst().orElseThrow();
        assertTrue(header.contains("Secure") && header.contains("SameSite=None") && header.contains("Partitioned"), header);
        if ("SESSION".equals(name)) assertTrue(header.contains("HttpOnly"), header);
        return new Cookie(name, header.substring(name.length() + 1, header.indexOf(';')));
    }
}
