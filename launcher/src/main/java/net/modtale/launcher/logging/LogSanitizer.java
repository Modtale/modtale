package net.modtale.launcher.logging;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class LogSanitizer {

    private static final Set<String> SENSITIVE_QUERY_KEYS = Set.of(
            "code",
            "password",
            "pre_auth_token",
            "preauthtoken",
            "state",
            "token",
            "xsrf_token"
    );

    private LogSanitizer() {
    }

    public static String uri(URI uri) {
        return uri == null ? "" : url(uri.toString());
    }

    public static String url(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }

        try {
            URI uri = URI.create(rawUrl);
            StringBuilder sanitized = new StringBuilder();
            if (uri.getScheme() != null) {
                sanitized.append(uri.getScheme()).append("://");
            }
            if (uri.getRawAuthority() != null) {
                sanitized.append(uri.getRawAuthority());
            }
            if (uri.getRawPath() != null) {
                sanitized.append(uri.getRawPath());
            }
            String query = sanitizeQuery(uri.getRawQuery());
            if (!query.isBlank()) {
                sanitized.append('?').append(query);
            }
            return sanitized.toString();
        } catch (RuntimeException ignored) {
            return rawUrl.replaceAll("(?i)(token|code|password|secret)=([^&\\s]+)", "$1=[redacted]");
        }
    }

    public static String bodyPreview(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String compact = body
                .replaceAll("(?i)\"(password|token|secret|preAuthToken|pre_auth_token)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"[redacted]\"")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s{2,}", " ")
                .trim();
        return compact.length() <= 1200 ? compact : compact.substring(0, 1200) + "...";
    }

    private static String sanitizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }

        StringBuilder sanitized = new StringBuilder();
        for (String part : rawQuery.split("&")) {
            if (part.isBlank()) {
                continue;
            }
            int equals = part.indexOf('=');
            String key = equals >= 0 ? part.substring(0, equals) : part;
            String value = equals >= 0 ? part.substring(equals + 1) : "";
            if (!sanitized.isEmpty()) {
                sanitized.append('&');
            }
            sanitized.append(key);
            if (equals >= 0) {
                sanitized.append('=');
                sanitized.append(isSensitiveQueryKey(key) ? "[redacted]" : value);
            }
        }
        return sanitized.toString();
    }

    private static boolean isSensitiveQueryKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).replace("-", "_");
        return SENSITIVE_QUERY_KEYS.contains(normalized)
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password");
    }
}
