package net.modtale.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

final class PublicReadCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {
  private static final Set<String> PUBLIC_PATHS =
      Set.of(
          "/api/v1/projects",
          "/api/v1/tags",
          "/api/v1/analytics/platform/stats",
          "/api/v1/meta/classifications",
          "/api/v1/meta/game-versions",
          "/api/v1/meta/game-versions/catalog");
  private static final Pattern PROJECT_READ_PATH =
      Pattern.compile(
          "^/api/v1/projects/[^/]+(?:/(?:details|versions|comments|gallery|team|meta|versions/changelogs))?$");
  private final CsrfTokenRequestAttributeHandler eager = new CsrfTokenRequestAttributeHandler();

  PublicReadCsrfTokenRequestHandler() {
    eager.setCsrfRequestAttributeName(null);
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      Supplier<CsrfToken> deferredCsrfToken) {
    if (isAnonymousPublicRead(request)) {
      super.handle(request, response, deferredCsrfToken);
    } else {
      eager.handle(request, response, deferredCsrfToken);
    }
  }

  static boolean isAnonymousPublicRead(HttpServletRequest request) {
    if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) return false;
    String path = request.getRequestURI();
    if (!(PUBLIC_PATHS.contains(path) || PROJECT_READ_PATH.matcher(path).matches())) return false;
    if ("HEAD".equals(request.getMethod())
        && !(path.equals("/api/v1/projects")
            || path.equals("/api/v1/tags")
            || PROJECT_READ_PATH.matcher(path).matches())) return false;
    if (request.getHeader(HttpHeaders.ORIGIN) != null
        || request.getHeader(HttpHeaders.REFERER) != null
        || request.getHeader(HttpHeaders.COOKIE) != null
        || request.getHeader(HttpHeaders.AUTHORIZATION) != null
        || request.getHeader("X-Modtale-Key") != null
        || request.getHeader("X-API-Key") != null
        || request.getSession(false) != null
        || request.getUserPrincipal() != null) return false;
    Enumeration<String> headerNames = request.getHeaderNames();
    if (headerNames == null) return false;
    while (headerNames.hasMoreElements()) {
      String name = headerNames.nextElement();
      if (name != null && name.regionMatches(true, 0, "Sec-Fetch-", 0, 10)) return false;
    }
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication == null || authentication instanceof AnonymousAuthenticationToken;
  }
}
