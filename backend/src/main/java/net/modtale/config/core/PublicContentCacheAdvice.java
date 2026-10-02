package net.modtale.config.core;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Locale;
import net.modtale.service.system.PublicContentCacheInvalidator;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Cache tags classify already-public responses; they never bypass controller authorization. */
@ControllerAdvice
public class PublicContentCacheAdvice implements ResponseBodyAdvice<Object> {
  static final String PUBLIC_CACHE_CONTROL = "public, max-age=0, s-maxage=300, must-revalidate";
  private final PublicContentCacheInvalidator invalidator;

  public PublicContentCacheAdvice(PublicContentCacheInvalidator invalidator) {
    this.invalidator = invalidator;
  }

  @Override
  public boolean supports(
      MethodParameter parameter, Class<? extends HttpMessageConverter<?>> converter) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter parameter,
      MediaType contentType,
      Class<? extends HttpMessageConverter<?>> converter,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (!(request instanceof ServletServerHttpRequest servletRequest)) return body;
    HttpServletRequest servlet = servletRequest.getServletRequest();
    String path = servlet.getRequestURI();
    if (!isContentRead(path, servlet.getMethod())) return body;
    invalidator.flushPending();
    HttpHeaders headers = response.getHeaders();
    if (response instanceof ServletServerHttpResponse servletResponse
        && servletResponse.getServletResponse().getStatus() >= 400) {
      headers.setCacheControl("no-store");
      headers.remove("Cache-Tag");
      return body;
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean personalized =
        (auth != null && !(auth instanceof AnonymousAuthenticationToken))
            || servlet.getHeader(HttpHeaders.AUTHORIZATION) != null
            || servlet.getHeader("X-Modtale-Key") != null
            || servlet.getHeader("X-API-Key") != null
            || servlet.getHeader(HttpHeaders.COOKIE) != null
            || servlet.getSession(false) != null;
    if (personalized || headers.containsHeader(HttpHeaders.SET_COOKIE)) {
      headers.setCacheControl("private, no-store");
      headers.remove("Cache-Tag");
      return body;
    }
    String cacheControl = headers.getCacheControl();
    // Only these endpoints are guaranteed public by construction without controller cache headers.
    boolean explicitlyPublicEndpoint =
        path.equals("/api/v1/news")
            || path.matches("^/api/v1/news/[^/]+$")
            || path.matches("^/api/v1/user/profile/[^/]+$")
            || path.matches("^/api/v1/creators/[^/]+/projects$");
    boolean declaredPublic =
        cacheControl != null
            && Arrays.stream(cacheControl.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch("public"::equals);
    if (cacheControl != null
        && (cacheControl.contains("private") || cacheControl.contains("no-store"))) {
      headers.setCacheControl("private, no-store");
      headers.remove("Cache-Tag");
      return body;
    }
    if (body != null
        && (declaredPublic || explicitlyPublicEndpoint)
        && !MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(contentType)) {
      headers.setCacheControl(PUBLIC_CACHE_CONTROL);
      String tag = invalidator.apiCacheTag();
      if (tag != null) headers.add("Cache-Tag", tag);
    } else {
      headers.setCacheControl("private, no-store");
      headers.remove("Cache-Tag");
    }
    return body;
  }

  private static boolean isContentRead(String path, String method) {
    if (!("GET".equals(method) || "HEAD".equals(method))) return false;
    return path.equals("/api/v1/projects")
        || path.equals("/api/v1/tags")
        || path.equals("/api/v1/analytics/platform/stats")
        || path.startsWith("/api/v1/projects/")
        || path.equals("/api/v1/news")
        || path.startsWith("/api/v1/news/")
        || path.startsWith("/api/v1/wiki/")
        || path.startsWith("/api/v1/meta/")
        || path.startsWith("/api/v1/creators/")
        || path.startsWith("/api/v1/user/profile/")
        || path.startsWith("/api/v1/og/project/");
  }
}
