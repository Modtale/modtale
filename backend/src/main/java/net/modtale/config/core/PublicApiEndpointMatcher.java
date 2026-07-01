package net.modtale.config.core;

import java.util.List;
import java.util.Locale;

public final class PublicApiEndpointMatcher {

    private static final List<String> PUBLIC_AUTH_PATHS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/verify",
            "/api/v1/auth/signin",
            "/api/v1/auth/mfa/validate-login",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password"
    );

    private static final List<String> PUBLIC_READ_EXACT_PATHS = List.of(
            "/api/v1/tags",
            "/api/v1/status",
            "/api/v1/analytics/platform/stats",
            "/api/v1/projects"
    );

    private static final List<String> PUBLIC_READ_PREFIXES = List.of(
            "/api/v1/projects/",
            "/api/v1/files/",
            "/api/v1/user/profile/",
            "/api/v1/users/",
            "/api/v1/creators/",
            "/api/v1/og/",
            "/api/v1/download/",
            "/api/v1/download-bundle/",
            "/api/v1/lists/",
            "/api/v1/meta/",
            "/api/v1/version/",
            "/api/v1/wiki/"
    );

    private PublicApiEndpointMatcher() {
    }

    public static boolean isPublicOperation(String path, String method) {
        if (path == null || method == null) {
            return false;
        }

        String normalizedPath = path.trim();
        String normalizedMethod = method.toUpperCase(Locale.ROOT);

        if (normalizedMethod.equals("POST") && normalizedPath.equals("/api/v1/users/batch")) {
            return true;
        }

        if (PUBLIC_AUTH_PATHS.contains(normalizedPath)) {
            return true;
        }

        if (normalizedMethod.equals("GET") || normalizedMethod.equals("HEAD")) {
            if (PUBLIC_READ_EXACT_PATHS.contains(normalizedPath)
                    || PUBLIC_READ_PREFIXES.stream().anyMatch(normalizedPath::startsWith)) {
                return true;
            }

            return normalizedPath.matches("^/api/v1/orgs/[^/]+/members$");
        }

        return false;
    }
}
