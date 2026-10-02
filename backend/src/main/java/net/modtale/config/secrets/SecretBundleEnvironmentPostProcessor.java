package net.modtale.config.secrets;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Loads an explicitly enabled, immutable startup snapshot from fixed mounted files. */
public final class SecretBundleEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String SOURCE_NAME = "modtaleSecretBundles";
    static final String ENABLED = "MODTALE_SECRET_BUNDLES_ENABLED";
    static final String PROFILE = "MODTALE_SECRET_BUNDLE_PROFILE";
    static final String PREVIEW_ID = "MODTALE_SECRET_BUNDLE_PREVIEW_ID";
    static final int MAX_BYTES = 65536;
    static final String SAFE_ERROR = "Secret bundle configuration is invalid.";
    static final Set<String> SHARED = words("ADMIN_DISCORD_WEBHOOK_URL BLUESKY_CLIENT_ID DISCORD_CLIENT_ID DISCORD_CLIENT_SECRET DISCORD_WEBHOOK_URL GITHUB_CLIENT_ID GITHUB_CLIENT_SECRET GITLAB_CLIENT_ID GITLAB_CLIENT_SECRET GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET GOOGLE_CLIENT_SECREY HYTALEMODDING_KEY MODTALE_PUBLIC_CACHE_PURGE_TOKEN MONGODB_URI MONGO_URI PRE_AUTH_SECRET R2_ACCESS_KEY R2_ENDPOINT R2_SECRET_KEY SMTP_HOST SMTP_PASSWORD SMTP_USERNAME TWITTER_CLIENT_ID TWITTER_CLIENT_SECRET WARDEN_API_KEY WARDEN_URL WEBHOOK_URL WIKI_API_KEY modtale-github-secret modtale-mongo-uri");
    static final Set<String> BRANCH_BASE = words("BRANCH_PREVIEW_MONGODB_URI R2_SOURCE_READ_ACCESS_KEY R2_SOURCE_READ_ENDPOINT R2_SOURCE_READ_SECRET_KEY R2_TEMPLATE_READ_ACCESS_KEY R2_TEMPLATE_READ_ENDPOINT R2_TEMPLATE_READ_SECRET_KEY");
    static final Set<String> PR_BASE = words("PREVIEW_MONGODB_URI PREVIEW_SOURCE_R2_ACCESS_KEY PREVIEW_SOURCE_R2_ENDPOINT PREVIEW_SOURCE_R2_SECRET_KEY");
    private static final Map<String, Set<String>> BOUNDARIES = Map.of(
            "shared", SHARED, "production", Set.of("HYTALE_CLIENT_SECRET"),
            "branch-preview", BRANCH_BASE, "pr-preview", PR_BASE);
    private static final Map<String, String> COMMON_PROPERTIES = Map.ofEntries(
            Map.entry("MONGODB_URI", "spring.mongodb.uri"),
            Map.entry("R2_ACCESS_KEY", "app.r2.access-key"),
            Map.entry("R2_SECRET_KEY", "app.r2.secret-key"),
            Map.entry("R2_ENDPOINT", "app.r2.endpoint"),
            Map.entry("SMTP_HOST", "spring.mail.host"),
            Map.entry("SMTP_USERNAME", "spring.mail.username"),
            Map.entry("SMTP_PASSWORD", "spring.mail.password"),
            Map.entry("PRE_AUTH_SECRET", "app.security.pre-auth-secret"),
            Map.entry("WARDEN_URL", "app.warden.url"),
            Map.entry("WIKI_API_KEY", "app.hytalemodding.wiki-key"),
            Map.entry("DISCORD_WEBHOOK_URL", "app.discord-webhook.url"),
            Map.entry("ADMIN_DISCORD_WEBHOOK_URL", "app.admin-discord-webhook.url"));
    private static final Map<String, String> PRODUCTION_PROPERTIES = Map.of(
            "WEBHOOK_URL", "app.webhook.url", "HYTALEMODDING_KEY", "app.webhook.key");
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final BundleReader reader;

    public SecretBundleEnvironmentPostProcessor() {
        this(path -> {
            try (var input = Files.newInputStream(path)) {
                return input.readNBytes(MAX_BYTES + 1);
            }
        });
    }

    SecretBundleEnvironmentPostProcessor(BundleReader reader) {
        this.reader = reader;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(SOURCE_NAME)) {
            return;
        }
        try {
            String enabled = environment.getProperty(ENABLED, "false");
            if ("false".equals(enabled)) {
                return;
            }
            if (!"true".equals(enabled)) {
                throw invalid();
            }
            String profile = environment.getProperty(PROFILE);
            Map<String, Object> properties = new LinkedHashMap<>();
            if ("prod".equals(profile) || "dev".equals(profile)) {
                if (!environment.getProperty(PREVIEW_ID, "").isEmpty()) {
                    throw invalid();
                }
                // Keep backend/scanner rotation coupled to the original secret binding.
                // The audited bundle retains its copied key, but must never activate it.
                String originalWardenKey = environment.getProperty("WARDEN_API_KEY");
                if (originalWardenKey == null || originalWardenKey.isEmpty()) {
                    throw invalid();
                }
                Map<String, String> shared = load("shared");
                require(shared, SHARED.stream().filter(key -> !key.equals("MODTALE_PUBLIC_CACHE_PURGE_TOKEN") && !key.equals("WARDEN_API_KEY")).toList());
                if ("true".equals(environment.getProperty("PUBLIC_CACHE_PURGE_ENABLED", "false"))
                        || "true".equals(environment.getProperty("app.public-cache-purge.enabled", "false"))) {
                    require(shared, Set.of("MODTALE_PUBLIC_CACHE_PURGE_TOKEN"));
                }
                COMMON_PROPERTIES.forEach((key, property) -> put(properties, key, property, shared.get(key)));
                for (String provider : new String[]{"github", "gitlab", "discord", "twitter", "google", "bluesky"}) {
                    String prefix = provider.toUpperCase(java.util.Locale.ROOT) + "_CLIENT_";
                    put(properties, prefix + "ID", "spring.security.oauth2.client.registration." + provider + ".client-id", shared.get(prefix + "ID"));
                    if (!provider.equals("bluesky")) {
                        put(properties, prefix + "SECRET", "spring.security.oauth2.client.registration." + provider + ".client-secret", shared.get(prefix + "SECRET"));
                    }
                }
                put(properties, "CLOUDFLARE_CACHE_PURGE_TOKEN", "app.public-cache-purge.token", shared.getOrDefault("MODTALE_PUBLIC_CACHE_PURGE_TOKEN", ""));
                if ("prod".equals(profile)) {
                    PRODUCTION_PROPERTIES.forEach((key, property) -> put(properties, key, property, shared.get(key)));
                    Map<String, String> production = load("production");
                    require(production, Set.of("HYTALE_CLIENT_SECRET"));
                    put(properties, "HYTALE_CLIENT_SECRET", "spring.security.oauth2.client.registration.hytale.client-secret", production.get("HYTALE_CLIENT_SECRET"));
                }
            } else if ("branch-preview".equals(profile) || "pr-preview".equals(profile)) {
                String id = environment.getProperty(PREVIEW_ID);
                boolean branch = profile.equals("branch-preview");
                if (id == null || !(branch ? id.matches("[a-z0-9](?:[a-z0-9-]{0,18}[a-z0-9])?") : id.matches("[1-9][0-9]*"))) {
                    throw invalid();
                }
                if (branch && Set.of("main", "develop", "dev").contains(id)) {
                    throw invalid();
                }
                Map<String, String> secrets = load(profile);
                require(secrets, branch ? BRANCH_BASE : PR_BASE);
                String prefix = profile + "-" + id + "-r2-";
                require(secrets, Set.of(prefix + "access-key", prefix + "secret-key", prefix + "endpoint", prefix + "token-id"));
                put(properties, "MONGODB_URI", "spring.mongodb.uri", secrets.get(branch ? "BRANCH_PREVIEW_MONGODB_URI" : "PREVIEW_MONGODB_URI"));
                put(properties, "R2_ACCESS_KEY", "app.r2.access-key", secrets.get(prefix + "access-key"));
                put(properties, "R2_SECRET_KEY", "app.r2.secret-key", secrets.get(prefix + "secret-key"));
                put(properties, "R2_ENDPOINT", "app.r2.endpoint", secrets.get(prefix + "endpoint"));
                String source = branch ? "R2_TEMPLATE_READ_" : "PREVIEW_SOURCE_R2_";
                put(properties, "APP_SEEDING_SOURCE_R2_ACCESS_KEY", "app.seeding.source-r2-access-key", secrets.get(source + "ACCESS_KEY"));
                put(properties, "APP_SEEDING_SOURCE_R2_SECRET_KEY", "app.seeding.source-r2-secret-key", secrets.get(source + "SECRET_KEY"));
                put(properties, "APP_SEEDING_SOURCE_R2_ENDPOINT", "app.seeding.source-r2-endpoint", secrets.get(source + "ENDPOINT"));
            } else {
                throw invalid();
            }
            environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, Map.copyOf(properties)) {
                @Override
                public String toString() {
                    return SOURCE_NAME;
                }
            });
        } catch (IOException | RuntimeException failure) {
            // Parser/I/O exceptions may contain payload fragments. Do not retain their causes.
            throw invalid();
        }
    }

    private Map<String, String> load(String boundary) throws IOException {
        byte[] raw = reader.read(Path.of("/app/secrets/bundles", boundary + ".json"));
        if (raw.length > MAX_BYTES) {
            throw invalid();
        }
        String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
        JsonNode root = JSON.readTree(json);
        if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(Set.of("schemaVersion", "boundary", "secrets"))
                || !root.get("schemaVersion").isIntegralNumber() || !root.get("schemaVersion").asString().equals("1")
                || !root.get("boundary").isTextual() || !boundary.equals(root.get("boundary").stringValue())
                || !root.get("secrets").isObject() || root.get("secrets").isEmpty()) {
            throw invalid();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (var entry : root.get("secrets").properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (!allowed(boundary, key) || !value.isTextual() || value.stringValue().isEmpty()
                    || value.stringValue().indexOf('\0') >= 0
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(value.stringValue())) {
                throw invalid();
            }
            values.put(key, value.stringValue());
        }
        return values;
    }

    private static boolean allowed(String boundary, String key) {
        return BOUNDARIES.get(boundary).contains(key)
                || (boundary.equals("branch-preview") && key.matches("branch-preview-[a-z0-9](?:[a-z0-9-]{0,18}[a-z0-9])?-r2-(access-key|secret-key|endpoint|token-id)"))
                || (boundary.equals("pr-preview") && key.matches("pr-preview-[1-9][0-9]*-r2-(access-key|secret-key|endpoint|token-id)"));
    }

    private static void require(Map<String, String> values, Iterable<String> keys) {
        for (String key : keys) {
            if (!values.containsKey(key)) {
                throw invalid();
            }
        }
    }

    private static void put(Map<String, Object> properties, String alias, String property, String value) {
        String literal = escapePlaceholders(value);
        properties.put(alias, literal);
        // Direct canonical keys avoid extra interpolation through application.properties aliases.
        properties.put(property, literal);
    }

    static String escapePlaceholders(String value) {
        // Spring removes exactly one escape before each balanced ${...} prefix.
        // Existing backslashes must remain, and incomplete prefixes must not be escaped.
        boolean[] escape = new boolean[value.length()];
        int[] openings = new int[value.length()];
        int depth = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '{') {
                openings[depth++] = index;
            } else if (value.charAt(index) == '}' && depth > 0) {
                int opening = openings[--depth];
                if (opening > 0 && value.charAt(opening - 1) == '$') {
                    escape[opening - 1] = true;
                }
            }
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            if (escape[index]) {
                escaped.append('\\');
            }
            escaped.append(value.charAt(index));
        }
        return escaped.toString();
    }

    private static Set<String> words(String value) {
        return Set.of(value.split(" "));
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException(SAFE_ERROR);
    }

    @FunctionalInterface
    interface BundleReader {
        byte[] read(Path path) throws IOException;
    }
}
