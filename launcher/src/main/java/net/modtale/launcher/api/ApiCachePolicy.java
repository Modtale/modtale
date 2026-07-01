package net.modtale.launcher.api;

import java.time.Duration;

final class ApiCachePolicy {

    private ApiCachePolicy() {
    }

    static Duration ttlFor(String pathAndQuery) {
        if (!isPublicCacheableGet(pathAndQuery)) {
            return Duration.ZERO;
        }
        if (pathAndQuery.startsWith("/meta/")) {
            return Duration.ofHours(24);
        }
        if (pathAndQuery.startsWith("/projects")) {
            return Duration.ofHours(26);
        }
        return Duration.ofHours(2);
    }

    static boolean isEnabled(Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    private static boolean isPublicCacheableGet(String pathAndQuery) {
        String normalized = pathAndQuery == null ? "" : pathAndQuery;
        return normalized.startsWith("/meta/")
                || (normalized.startsWith("/projects")
                && !normalized.contains("/comments")
                && !normalized.contains("/download-url")
                && !normalized.contains("/download-bundle-url"));
    }
}
