package net.modtale.config.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import net.modtale.config.auth.ApiKeyAuthFilter;
import net.modtale.config.core.PublicContentCacheAdvice;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.controller.auth.CsrfController;
import net.modtale.service.auth.*;
import net.modtale.service.system.PublicContentCacheInvalidator;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class PublicReadCsrfSecurityTest {
  private AnnotationConfigWebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.register(TestSecurity.class);
    context.refresh();
    mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    context.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/projects", "/api/v1/tags", "/api/v1/analytics/platform/stats"})
  void eligibleGetAndHeadRemainCookieFreeAndPublic(String path) throws Exception {
    List<String> methods =
        path.equals("/api/v1/analytics/platform/stats") ? List.of("GET") : List.of("GET", "HEAD");
    for (String method : methods) {
      var result =
          mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path))
              .andExpect(status().isOk())
              .andExpect(
                  header()
                      .string("Cache-Control", "public, max-age=0, s-maxage=300, must-revalidate"))
              .andExpect(header().string("Cache-Tag", "modtale-api-dev.api.modtale.net"))
              .andExpect(header().doesNotExist("Set-Cookie"))
              .andReturn();
      assertNull(result.getRequest().getSession(false));
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Origin",
        "Cookie",
        "Authorization",
        "X-Modtale-Key",
        "X-API-Key",
        "Referer",
        "Sec-Fetch-Site",
        "Sec-Fetch-Mode",
        "Sec-Fetch-Dest",
        "Sec-Fetch-User",
        "Sec-Fetch-Unknown"
      })
  void credentialAndOriginReadsRetainEagerIssuanceAndNoStore(String name) throws Exception {
    String value = name.equals("Origin") ? "https://preview-example.run.app" : "fixture";
    mvc.perform(get("/api/v1/projects").header(name, value))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "private, no-store"))
        .andExpect(header().doesNotExist("Cache-Tag"))
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("XSRF-TOKEN=")));
  }

  @Test
  void existingSessionsRetainIssuanceAndNoStore() throws Exception {
    for (MockHttpSession session : List.of(new MockHttpSession(), authenticatedSession())) {
      mvc.perform(get("/api/v1/projects").session(session))
          .andExpect(status().isOk())
          .andExpect(header().string("Cache-Control", "private, no-store"))
          .andExpect(header().doesNotExist("Cache-Tag"))
          .andExpect(
              header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("XSRF-TOKEN=")));
    }
  }

  @Test
  void privateAndUnknownReadsRetainEagerIssuance() throws Exception {
    mvc.perform(get("/api/v1/user/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("XSRF-TOKEN=")));
    for (String path :
        List.of("/api/v1/analytics/platform/stats", "/api/v1/meta/classifications")) {
      mvc.perform(head(path))
          .andExpect(status().isUnauthorized())
          .andExpect(
              header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("XSRF-TOKEN=")));
    }
    mvc.perform(get("/api/v1/status"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("XSRF-TOKEN=")));
  }

  @Test
  void explicitBootstrapStillIssuesCookieAndNoStore() throws Exception {
    var result =
        mvc.perform(get("/api/v1/auth/csrf").header("Origin", "https://preview-example.run.app"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(
                header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("XSRF-TOKEN=")))
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn();
    assertNull(result.getRequest().getSession(false));
  }

  @Test
  void missingPublicContentIsCookieFreeAndNoStore() throws Exception {
    mvc.perform(get("/api/v1/projects/fixed-missing"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().doesNotExist("Cache-Tag"))
        .andExpect(header().doesNotExist("Set-Cookie"));
  }

  @Test
  void protectedMutationStillRejectsMissingTokenAndAcceptsValidToken() throws Exception {
    mvc.perform(post("/api/v1/projects/test").session(authenticatedSession()))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/projects/test")
                .session(authenticatedSession())
                .cookie(new Cookie("XSRF-TOKEN", "fixture-csrf"))
                .header("X-XSRF-TOKEN", "wrong-csrf"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/projects/test")
                .session(authenticatedSession())
                .cookie(new Cookie("XSRF-TOKEN", "fixture-csrf"))
                .header("X-XSRF-TOKEN", "fixture-csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mutationReached").value(true));
  }

  @Test
  void generatedBootstrapTokenStillAuthorizesProtectedMutation() throws Exception {
    var bootstrap =
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
    String cookie = bootstrap.getHeader("Set-Cookie");
    assertNotNull(cookie);
    String token = cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';'));
    assertTrue(bootstrap.getContentAsString().contains("\"token\":\"" + token + "\""));
    mvc.perform(
            post("/api/v1/projects/test")
                .session(authenticatedSession())
                .cookie(new Cookie("XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mutationReached").value(true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://attacker.example", ""})
  void untrustedOriginCannotBootstrapCsrf(String origin) throws Exception {
    mvc.perform(get("/api/v1/auth/csrf").header("Origin", origin))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Set-Cookie"));
  }

  @Test
  void anonymousMatcherCoversOnlyExactPermittedReadShapes() {
    for (String path :
        List.of(
            "/api/v1/projects",
            "/api/v1/tags",
            "/api/v1/analytics/platform/stats",
            "/api/v1/meta/classifications",
            "/api/v1/meta/game-versions",
            "/api/v1/meta/game-versions/catalog")) {
      assertTrue(
          PublicReadCsrfTokenRequestHandler.isAnonymousPublicRead(
              new MockHttpServletRequest("GET", path)),
          path);
      assertEquals(
          path.equals("/api/v1/projects") || path.equals("/api/v1/tags"),
          PublicReadCsrfTokenRequestHandler.isAnonymousPublicRead(
              new MockHttpServletRequest("HEAD", path)),
          path);
    }
    for (String suffix :
        List.of(
            "",
            "/details",
            "/versions",
            "/comments",
            "/gallery",
            "/team",
            "/meta",
            "/versions/changelogs")) {
      for (String method : List.of("GET", "HEAD")) {
        assertTrue(
            PublicReadCsrfTokenRequestHandler.isAnonymousPublicRead(
                new MockHttpServletRequest(method, "/api/v1/projects/fixture" + suffix)),
            method + suffix);
      }
    }
    for (String path :
        List.of(
            "/api/v1/projects/",
            "/api/v1/projects//details",
            "/api/v1/projects/a/b/details",
            "/api/v1/projects/a/b/versions/changelogs",
            "/API/v1/projects",
            "/api/v1/projects/a/DETAILS")) {
      assertFalse(
          PublicReadCsrfTokenRequestHandler.isAnonymousPublicRead(
              new MockHttpServletRequest("GET", path)),
          path);
    }
  }

  @Test
  void matcherDoesNotBroadenMethodsPathsOrAuthentication() {
    for (String path :
        List.of(
            "/api/v1/analytics/platform/full",
            "/api/v1/projects/test/analytics",
            "/api/v1/projects/user/contributed",
            "/api/v1/meta/unknown",
            "/api/v1/auth/csrf",
            "/api/v1/user/me",
            "/api/v1/news",
            "/api/v1/wiki/test")) {
      assertFalse(
          PublicReadCsrfTokenRequestHandler.isAnonymousPublicRead(
              new MockHttpServletRequest("GET", path)));
    }
    for (String method : List.of("POST", "PUT", "DELETE", "PATCH", "OPTIONS")) {
      assertFalse(
          PublicReadCsrfTokenRequestHandler.isAnonymousPublicRead(
              new MockHttpServletRequest(method, "/api/v1/projects")));
    }
    for (String header :
        List.of(
            "Origin",
            "Cookie",
            "Authorization",
            "X-Modtale-Key",
            "X-API-Key",
            "Referer",
            "Sec-Fetch-Site",
            "Sec-Fetch-Unknown")) {
      var request = new MockHttpServletRequest("GET", "/api/v1/projects");
      request.addHeader(header, "");
      assertFalse(PublicReadCsrfTokenRequestHandler.isAnonymousPublicRead(request));
    }
    var request = new MockHttpServletRequest("GET", "/api/v1/projects");
    request.setUserPrincipal(() -> "container-user");
    assertFalse(PublicReadCsrfTokenRequestHandler.isAnonymousPublicRead(request));
    request.setUserPrincipal(null);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("unknown", null));
    assertFalse(PublicReadCsrfTokenRequestHandler.isAnonymousPublicRead(request));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "fixture", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    assertTrue(PublicReadCsrfTokenRequestHandler.isAnonymousPublicRead(request));
  }

  private static MockHttpSession authenticatedSession() {
    var session = new MockHttpSession();
    var security = SecurityContextHolder.createEmptyContext();
    security.setAuthentication(
        new UsernamePasswordAuthenticationToken(
            "fixture-user", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, security);
    return session;
  }

  @Configuration
  @EnableWebSecurity
  @EnableWebMvc
  static class TestSecurity {
    @Bean
    ClientRegistrationRepository clients() {
      return mock(ClientRegistrationRepository.class);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      var keys = mock(ApiKeyAuthFilter.class);
      var rates = mock(RateLimitFilter.class);
      for (Filter filter : List.of(keys, rates)) {
        doAnswer(
                invocation -> {
                  invocation
                      .getArgument(2, FilterChain.class)
                      .doFilter(
                          invocation.getArgument(0, ServletRequest.class),
                          invocation.getArgument(1, ServletResponse.class));
                  return null;
                })
            .when(filter)
            .doFilter(any(), any(), any());
      }
      var configuration =
          new SecurityConfig(
              keys,
              rates,
              mock(OAuth2LoginService.class),
              mock(OidcLoginService.class),
              mock(OAuth2AuthorizedClientRepository.class),
              mock(LocalUserDetailsService.class),
              mock(PasswordEncoder.class),
              mock(AccountService.class),
              mock(AuthenticationService.class),
              mock(LauncherAuthService.class),
              new AppFrontendProperties("https://preview-example.run.app"));
      return configuration.securityFilterChain(
          http, mock(OAuth2AuthorizationRequestResolver.class));
    }

    @Bean
    PublicContentCacheAdvice cacheAdvice() {
      var invalidator = mock(PublicContentCacheInvalidator.class);
      when(invalidator.apiCacheTag()).thenReturn("modtale-api-dev.api.modtale.net");
      return new PublicContentCacheAdvice(invalidator);
    }

    @Bean
    ContentController contentController() {
      return new ContentController();
    }

    @Bean
    CsrfController csrfController() {
      return new CsrfController();
    }
  }

  @RestController
  static class ContentController {
    @GetMapping({"/api/v1/projects", "/api/v1/tags", "/api/v1/analytics/platform/stats"})
    ResponseEntity<Map<String, Boolean>> publicRead() {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic())
          .body(Map.of("publicFixture", true));
    }

    @GetMapping("/api/v1/status")
    ResponseEntity<Map<String, Boolean>> privateRead() {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.noStore())
          .body(Map.of("privateFixture", true));
    }

    @GetMapping("/api/v1/projects/fixed-missing")
    ResponseEntity<Map<String, Boolean>> missingPublicContent() {
      return ResponseEntity.status(404)
          .cacheControl(CacheControl.noStore())
          .body(Map.of("missingFixture", true));
    }

    @PostMapping("/api/v1/projects/test")
    Map<String, Boolean> mutation() {
      return Map.of("mutationReached", true);
    }
  }
}
