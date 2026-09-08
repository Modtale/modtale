package net.modtale.launcher.settings;

import java.net.URI;
import java.util.Optional;
import net.modtale.launcher.api.ModtaleApiClient;

public final class LauncherConfig {

    public static final String API_BASE_URL_PROPERTY = "modtale.apiBaseUrl";
    public static final String API_BASE_URL_ENV = "MODTALE_API_BASE_URL";
    public static final String SITE_BASE_URL_PROPERTY = "modtale.siteBaseUrl";
    public static final String SITE_BASE_URL_ENV = "MODTALE_SITE_BASE_URL";
    public static final String LAUNCHER_UPDATES_REPOSITORY_PROPERTY = "modtale.launcherUpdatesRepository";
    public static final String LAUNCHER_UPDATES_REPOSITORY_ENV = "MODTALE_LAUNCHER_UPDATES_REPOSITORY";
    public static final String DISCORD_CLIENT_ID_PROPERTY = "modtale.discordClientId";
    public static final String DISCORD_CLIENT_ID_ENV = "MODTALE_DISCORD_CLIENT_ID";
    public static final String DISCORD_CLIENT_ID_FALLBACK_ENV = "DISCORD_CLIENT_ID";
    public static final String DEFAULT_LAUNCHER_UPDATES_REPOSITORY = "Modtale/modtale";

    private LauncherConfig() {
    }

    public static String siteBaseUrl() {
        return normalizeBaseUrl(firstConfiguredValue(SITE_BASE_URL_PROPERTY, SITE_BASE_URL_ENV),
                ModtaleApiClient.DEFAULT_SITE_BASE_URL,
                "Modtale website URL");
    }

    public static String apiBaseUrl() {
        return normalizeBaseUrl(firstConfiguredValue(API_BASE_URL_PROPERTY, API_BASE_URL_ENV),
                ModtaleApiClient.DEFAULT_API_BASE_URL,
                "Modtale API URL");
    }

    public static String launcherUpdatesRepository() {
        String value = firstConfiguredValue(LAUNCHER_UPDATES_REPOSITORY_PROPERTY, LAUNCHER_UPDATES_REPOSITORY_ENV);
        return normalizeLauncherUpdatesRepository(value);
    }

    public static Optional<String> discordClientId() {
        String value = firstConfiguredValue(DISCORD_CLIENT_ID_PROPERTY, DISCORD_CLIENT_ID_ENV);
        if (value == null) {
            value = environmentValue(DISCORD_CLIENT_ID_FALLBACK_ENV);
        }
        return normalizeDiscordClientId(value);
    }

    static String normalizeSiteBaseUrl(String rawValue) {
        return normalizeBaseUrl(rawValue, ModtaleApiClient.DEFAULT_SITE_BASE_URL, "Modtale website URL");
    }

    static String normalizeApiBaseUrl(String rawValue) {
        return normalizeBaseUrl(rawValue, ModtaleApiClient.DEFAULT_API_BASE_URL, "Modtale API URL");
    }

    public static String normalizeLauncherUpdatesRepository(String rawValue) {
        String value = rawValue == null || rawValue.isBlank()
                ? DEFAULT_LAUNCHER_UPDATES_REPOSITORY
                : rawValue.trim();
        if (!value.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Launcher update repository must use owner/repo format.");
        }
        return value;
    }

    public static Optional<String> normalizeDiscordClientId(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isBlank() || "dev".equalsIgnoreCase(value)) {
            return Optional.empty();
        }
        return value.matches("\\d{8,32}") ? Optional.of(value) : Optional.empty();
    }

    private static String normalizeBaseUrl(String rawValue, String defaultValue, String label) {
        String value = rawValue == null || rawValue.isBlank()
                ? defaultValue
                : rawValue.trim();
        if (!value.contains("://")) {
            value = defaultScheme(value) + "://" + value;
        }

        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("The " + label + " must start with http:// or https://.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Enter a valid " + label + ".");
        }
        return value.replaceAll("/+$", "");
    }

    private static String defaultScheme(String value) {
        String host = value;
        int pathIndex = host.indexOf('/');
        if (pathIndex >= 0) {
            host = host.substring(0, pathIndex);
        }
        if (host.startsWith("[") && host.contains("]")) {
            host = host.substring(1, host.indexOf(']'));
        } else {
            int portIndex = host.indexOf(':');
            if (portIndex >= 0) {
                host = host.substring(0, portIndex);
            }
        }

        String normalizedHost = host.toLowerCase(java.util.Locale.ROOT);
        if ("localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)) {
            return "http";
        }
        return "https";
    }

    private static String firstConfiguredValue(String systemPropertyName, String environmentVariableName) {
        String systemProperty = System.getProperty(systemPropertyName);
        if (systemProperty != null && !systemProperty.isBlank()) {
            return systemProperty;
        }

        String environmentValue = environmentValue(environmentVariableName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }
        return null;
    }

    private static String environmentValue(String environmentVariableName) {
        return System.getenv(environmentVariableName);
    }
}
