package net.modtale.config.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;

import java.util.Map;
import net.modtale.service.system.PublicContentCacheInvalidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class PublicContentCacheAdviceTest {
  private static final String[] CATALOG_PATHS = {
    "/api/v1/tags", "/api/v1/analytics/platform/stats"
  };
  private MockMvc mvc;
  private PublicContentCacheInvalidator invalidator;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    invalidator = mock(PublicContentCacheInvalidator.class);
    when(invalidator.apiCacheTag()).thenReturn("modtale-api-api.modtale.net");
    mvc =
        MockMvcBuilders.standaloneSetup(new ContentController())
            .setControllerAdvice(new PublicContentCacheAdvice(invalidator))
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void successfulAnonymousPublicContentHasBoundedEdgeTtlAndEnvironmentTag() throws Exception {
    for (String path :
        new String[] {
          "/api/v1/projects/test",
          "/api/v1/news/test",
          "/api/v1/user/profile/test",
          "/api/v1/creators/test/projects"
        }) {
      var response = mvc.perform(get(path)).andReturn().getResponse();
      assertEquals(200, response.getStatus());
      assertEquals(
          PublicContentCacheAdvice.PUBLIC_CACHE_CONTROL,
          response.getHeader(HttpHeaders.CACHE_CONTROL));
      assertEquals("modtale-api-api.modtale.net", response.getHeader("Cache-Tag"));
    }
    verify(invalidator, times(4)).flushPending();
  }

  @Test
  void anonymousTagsAndPlatformStatsCapDeclaredPublicTtlAndCarryTheApiTag() throws Exception {
    for (String path : CATALOG_PATHS) {
      for (var request :
          new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
            get(path), head(path)
          }) {
        var response = mvc.perform(request).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals(
            PublicContentCacheAdvice.PUBLIC_CACHE_CONTROL,
            response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertEquals("modtale-api-api.modtale.net", response.getHeader("Cache-Tag"));
      }
    }
    verify(invalidator, times(4)).flushPending();
  }

  @Test
  void tagsAndPlatformStatsRejectCredentialCookieSessionAndAuthenticatedCaching() throws Exception {
    for (String path : CATALOG_PATHS) {
      for (String header :
          new String[] {
            HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE, "X-Modtale-Key", "X-API-Key"
          }) {
        var response = mvc.perform(get(path).header(header, "test")).andReturn().getResponse();
        assertEquals("private, no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertNull(response.getHeader("Cache-Tag"));
      }
      var response =
          mvc.perform(get(path).session(new MockHttpSession())).andReturn().getResponse();
      assertEquals("private, no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
      assertNull(response.getHeader("Cache-Tag"));
    }
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    for (String path : CATALOG_PATHS) {
      var response = mvc.perform(get(path)).andReturn().getResponse();
      assertEquals("private, no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
      assertNull(response.getHeader("Cache-Tag"));
    }
  }

  @Test
  void tagsAndPlatformStatsErrorsAndCookieSettingResponsesAreNeverShared() throws Exception {
    for (String path : CATALOG_PATHS) {
      for (String status : new String[] {"404", "500"}) {
        var response = mvc.perform(get(path).param("status", status)).andReturn().getResponse();
        assertEquals(Integer.parseInt(status), response.getStatus());
        assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertNull(response.getHeader("Cache-Tag"));
      }
      var response = mvc.perform(get(path).param("setCookie", "true")).andReturn().getResponse();
      assertEquals("private, no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
      assertNull(response.getHeader("Cache-Tag"));
    }
  }

  @Test
  void credentialedCookieSessionAndAuthenticatedResponsesAreNeverShared() throws Exception {
    for (String header :
        new String[] {
          HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE, "X-Modtale-Key", "X-API-Key"
        }) {
      var response =
          mvc.perform(get("/api/v1/projects/test").header(header, "test"))
              .andReturn()
              .getResponse();
      assertEquals("private, no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
      assertNull(response.getHeader("Cache-Tag"));
    }
    var sessionResponse =
        mvc.perform(get("/api/v1/projects/test").session(new MockHttpSession()))
            .andReturn()
            .getResponse();
    assertEquals("private, no-store", sessionResponse.getHeader(HttpHeaders.CACHE_CONTROL));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("user", null, java.util.List.of()));
    var authResponse = mvc.perform(get("/api/v1/projects/test")).andReturn().getResponse();
    assertEquals("private, no-store", authResponse.getHeader(HttpHeaders.CACHE_CONTROL));
    assertNull(authResponse.getHeader("Cache-Tag"));
  }

  @Test
  void privateErrors404AndCookieSettingResponsesAreNoStore() throws Exception {
    for (String path :
        new String[] {
          "/api/v1/projects/private",
          "/api/v1/news/missing",
          "/api/v1/projects/error",
          "/api/v1/projects/cookie"
        }) {
      var response = mvc.perform(get(path)).andReturn().getResponse();
      assertTrue(response.getHeader(HttpHeaders.CACHE_CONTROL).contains("no-store"));
      assertNull(response.getHeader("Cache-Tag"));
    }
  }

  @Test
  void downloadAndAdminResponsesAreOutsideThePublicContentCacheSurface() throws Exception {
    for (String path :
        new String[] {
          "/api/v1/download/token",
          "/api/v1/admin/news",
          "/api/v1/analytics/platform/full",
          "/api/v1/tags/extra",
          "/api/v1/analytics/platform/stats/extra"
        }) {
      var response = mvc.perform(get(path)).andReturn().getResponse();
      assertEquals("no-store", response.getHeader(HttpHeaders.CACHE_CONTROL));
      assertNull(response.getHeader("Cache-Tag"));
    }
    verify(invalidator, never()).flushPending();
  }

  @RestController
  static class ContentController {
    @GetMapping({"/api/v1/tags", "/api/v1/analytics/platform/stats"})
    ResponseEntity<?> catalog(
        @RequestParam(name = "status", defaultValue = "200") int status,
        @RequestParam(name = "setCookie", defaultValue = "false") boolean setCookie) {
      var response =
          ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400");
      if (setCookie) response.header(HttpHeaders.SET_COOKIE, "session=test");
      return response.body(Map.of("count", 10));
    }

    @GetMapping("/api/v1/projects/test")
    ResponseEntity<?> project() {
      return ResponseEntity.ok()
          .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
          .body(Map.of("title", "Public project"));
    }

    @GetMapping({
      "/api/v1/news/test",
      "/api/v1/user/profile/test",
      "/api/v1/creators/test/projects"
    })
    Map<String, String> publicContent() {
      return Map.of("title", "Public content");
    }

    @GetMapping("/api/v1/projects/private")
    ResponseEntity<?> privateContent() {
      return ResponseEntity.ok()
          .header(HttpHeaders.CACHE_CONTROL, "no-cache")
          .body(Map.of("draft", "private"));
    }

    @GetMapping("/api/v1/news/missing")
    ResponseEntity<?> missing() {
      return ResponseEntity.notFound().build();
    }

    @GetMapping("/api/v1/projects/error")
    ResponseEntity<?> error() {
      return ResponseEntity.internalServerError().body(Map.of("error", "failed"));
    }

    @GetMapping("/api/v1/projects/cookie")
    ResponseEntity<?> cookie() {
      return ResponseEntity.ok()
          .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
          .header(HttpHeaders.SET_COOKIE, "session=test")
          .body(Map.of("title", "Public project"));
    }

    @GetMapping({
      "/api/v1/download/token",
      "/api/v1/admin/news",
      "/api/v1/analytics/platform/full",
      "/api/v1/tags/extra",
      "/api/v1/analytics/platform/stats/extra"
    })
    ResponseEntity<?> excluded() {
      return ResponseEntity.ok()
          .header(HttpHeaders.CACHE_CONTROL, "no-store")
          .body(Map.of("value", "private"));
    }
  }
}
