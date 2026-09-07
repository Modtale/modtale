package net.modtale.config.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

/** Exempts only operations that do not rely on an existing browser session. */
final class ApiCsrfRequestMatcher implements RequestMatcher {
    private static final Set<String> PUBLIC_POST_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/verify",
            "/api/v1/auth/signin",
            "/api/v1/auth/mfa/validate-login",
            "/api/v1/auth/launcher/exchange",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/users/batch",
            "/api/v1/projects/external/identify"
    );

    @Override
    public boolean matches(HttpServletRequest request) {
        if (!CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)) {
            return false;
        }
        String path = request.getRequestURI();
        if ("POST".equals(request.getMethod()) && PUBLIC_POST_PATHS.contains(path)) {
            return false;
        }
        String key = request.getHeader("X-MODTALE-KEY");
        // ApiKeyAuthFilter rejects invalid credentials instead of using the session.
        return !((path.equals("/api/v1") || path.startsWith("/api/v1/"))
                && key != null && !key.isBlank());
    }
}
