package net.modtale.config.core;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private static final int EXAMPLE_MAX_DEPTH = 5;
    private static final List<String> DOCS_EXCLUDED_PREFIXES = List.of(
            "/api/v1/admin/",
            "/api/v1/reports"
    );
    private static final List<String> DOCS_EXCLUDED_EXACT_PATHS = List.of(
            "/api/v1/admin",
            "/api/v1/analytics/platform/full",
            "/api/v1/projects/{id}/favorite",
            "/api/v1/user/follow/{targetId}",
            "/api/v1/user/unfollow/{targetId}"
    );

    private static final Map<String, Object> TIER_EXEMPT = Map.of(
            "name", "Exempt",
            "readPerMinute", "unlimited",
            "writePerMinute", "unlimited",
            "notes", "No request bucket applies for this route."
    );

    private static final Map<String, Object> TIER_PUBLIC_IP = Map.of(
            "name", "Public-IP",
            "readPerMinute", 60,
            "writePerMinute", 5,
            "notes", "Direct automated/public traffic without API key."
    );

    private static final Map<String, Object> TIER_FRONTEND_PUBLIC = Map.of(
            "name", "Frontend-Public",
            "readPerMinute", 3000,
            "writePerMinute", 40,
            "notes", "Browser traffic detected from Modtale frontend origins."
    );

    private static final Map<String, Object> TIER_USER_SESSION = Map.of(
            "name", "User-Session",
            "readPerMinute", 2000,
            "writePerMinute", 150,
            "notes", "Authenticated user session requests."
    );

    private static final Map<String, Object> TIER_STANDARD_API = Map.of(
            "name", "Standard-API",
            "readPerMinute", 600,
            "writePerMinute", 60,
            "notes", "Authenticated API key traffic (standard tier)."
    );

    private static final Map<String, Object> TIER_ENTERPRISE_API = Map.of(
            "name", "Enterprise-API",
            "readPerMinute", 5000,
            "writePerMinute", 500,
            "notes", "Authenticated API key traffic (enterprise tier)."
    );

    @Bean
    public OpenAPI modtaleOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Modtale API")
                        .version("v1")
                        .description("Auto-generated API reference from current backend controllers. Swagger UI and the custom API docs page both consume this same generated spec.")
                        .license(new License().name("AGPL-3.0").url("https://www.gnu.org/licenses/agpl-3.0.en.html")))
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-MODTALE-KEY")
                                .description("API key generated from the Modtale developer dashboard."))
                        .addSecuritySchemes("SessionAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Session-based authentication cookie used by browser clients.")))
                .addServersItem(new Server().url("https://api.modtale.net").description("Production"))
                .addServersItem(new Server().url("http://localhost:8080").description("Local Development"))
                .addTagsItem(new Tag().name("Auth & Session"))
                .addTagsItem(new Tag().name("Projects, Versions & Downloads"))
                .addTagsItem(new Tag().name("Users & Profiles"))
                .addTagsItem(new Tag().name("Organizations & Connections"))
                .addTagsItem(new Tag().name("Notifications & Reports"))
                .addTagsItem(new Tag().name("Metadata & System"));
    }

    @Bean
    public OpenApiCustomizer apiDocsOpenApiCustomizer() {
        return openApi -> {
            Paths paths = openApi.getPaths();
            if (paths == null) {
                return;
            }

            paths.entrySet().removeIf(entry -> isAdminOnlyDocPath(entry.getKey()));
            pruneUnusedSchemas(openApi);

            paths.forEach((path, pathItem) -> {
                if (pathItem == null) {
                    return;
                }

                pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                    String method = httpMethod.name();
                    boolean isPublic = isPublicOperation(path, method);
                    operation.addExtension("x-modtale-access", isPublic ? "public" : "auth");
                    operation.addExtension("x-modtale-rate-limit-tiers", rateLimitTiers(path));

                    if (operation.getTags() == null || operation.getTags().isEmpty()) {
                        operation.setTags(List.of(sectionForPath(path)));
                    }
                });
            });

            enrichOperation(paths, "/api/v1/auth/signin", "POST",
                    "Sign in with username/email + password.",
                    Map.of("status", "success"),
                    Map.of("mfa_required", true, "pre_auth_token", "b8af319e-09a3-4f5a-9d87-9695dcab4711"),
                    Map.of("error", "Invalid credentials"));

            enrichOperation(paths, "/api/v1/projects", "GET",
                    "Searches and filters publicly visible projects. Supports tags, classification, pagination, and optional sort parameters.",
                    Map.of(
                            "content", List.of(
                                    Map.of("id", "f0a0f750-9f2c-4a54-8d90-bf42c21fb2f2", "title", "Skyforge Utilities", "classification", "PLUGIN")
                            ),
                            "page", Map.of("size", 10, "number", 0, "totalElements", 1, "totalPages", 1)
                    ),
                    null,
                    null);

            enrichOperation(paths, "/api/v1/projects/{id}", "GET",
                    "Returns a single project with metadata, permissions, and latest published state.",
                    null,
                    null,
                    Map.of("error", "Project not found"));

            enrichOperation(paths, "/api/v1/auth/register", "POST",
                    "Creates a new user account and sends verification instructions.",
                    Map.of("message", "User registered successfully", "username", "new_creator"),
                    null,
                    Map.of("error", "Username is already taken"));

            hydrateMissingJsonResponseExamples(openApi);
        };
    }

    private void hydrateMissingJsonResponseExamples(OpenAPI openApi) {
        Paths paths = openApi.getPaths();
        if (paths == null) {
            return;
        }

        Map<String, Schema> schemaRegistry = openApi.getComponents() != null && openApi.getComponents().getSchemas() != null
                ? openApi.getComponents().getSchemas()
                : Map.of();

        paths.forEach((ignoredPath, pathItem) -> {
            if (pathItem == null) {
                return;
            }

            pathItem.readOperations().forEach(operation -> {
                if (operation.getResponses() == null) {
                    return;
                }

                operation.getResponses().forEach((ignoredCode, response) -> {
                    if (response == null || response.getContent() == null) {
                        return;
                    }

                    response.getContent().forEach((contentType, mediaType) -> {
                        if (!isJsonLike(contentType) || mediaType == null) {
                            return;
                        }

                        if (mediaType.getExample() != null || (mediaType.getExamples() != null && !mediaType.getExamples().isEmpty())) {
                            return;
                        }

                        Object generated = buildExample(mediaType.getSchema(), schemaRegistry, new LinkedHashSet<>(), 0);
                        if (generated != null) {
                            mediaType.setExample(generated);
                        }
                    });
                });
            });
        });
    }

    private boolean isJsonLike(String contentType) {
        if (contentType == null) {
            return false;
        }

        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.contains("json") || normalized.endsWith("+json");
    }

    private void pruneUnusedSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }

        Map<String, Schema> schemaRegistry = openApi.getComponents().getSchemas();
        if (schemaRegistry.isEmpty()) {
            return;
        }

        Set<String> referencedSchemas = new LinkedHashSet<>();
        Paths paths = openApi.getPaths();
        if (paths != null) {
            paths.forEach((ignoredPath, pathItem) -> {
                if (pathItem == null) {
                    return;
                }

                pathItem.readOperations().forEach(operation -> collectOperationSchemaRefs(operation, schemaRegistry, referencedSchemas));
            });
        }

        schemaRegistry.keySet().retainAll(referencedSchemas);
    }

    private void collectOperationSchemaRefs(
            io.swagger.v3.oas.models.Operation operation,
            Map<String, Schema> schemaRegistry,
            Set<String> referencedSchemas
    ) {
        if (operation == null) {
            return;
        }

        if (operation.getParameters() != null) {
            operation.getParameters().forEach(parameter -> {
                if (parameter == null) {
                    return;
                }

                collectSchemaRefs(parameter.getSchema(), schemaRegistry, referencedSchemas);
                if (parameter.getContent() != null) {
                    parameter.getContent().values().forEach(mediaType -> {
                        if (mediaType != null) {
                            collectSchemaRefs(mediaType.getSchema(), schemaRegistry, referencedSchemas);
                        }
                    });
                }
            });
        }

        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            operation.getRequestBody().getContent().values().forEach(mediaType -> {
                if (mediaType != null) {
                    collectSchemaRefs(mediaType.getSchema(), schemaRegistry, referencedSchemas);
                }
            });
        }

        if (operation.getResponses() != null) {
            operation.getResponses().values().forEach(response -> {
                if (response == null || response.getContent() == null) {
                    return;
                }

                response.getContent().values().forEach(mediaType -> {
                    if (mediaType != null) {
                        collectSchemaRefs(mediaType.getSchema(), schemaRegistry, referencedSchemas);
                    }
                });
            });
        }
    }

    private void collectSchemaRefs(Schema<?> schema, Map<String, Schema> schemaRegistry, Set<String> referencedSchemas) {
        if (schema == null) {
            return;
        }

        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            String key = ref.contains("/") ? ref.substring(ref.lastIndexOf('/') + 1) : ref;
            if (!referencedSchemas.add(key)) {
                return;
            }

            collectSchemaRefs(schemaRegistry.get(key), schemaRegistry, referencedSchemas);
            return;
        }

        if (schema instanceof ComposedSchema composedSchema) {
            if (composedSchema.getAllOf() != null) {
                composedSchema.getAllOf().forEach(part -> collectSchemaRefs(part, schemaRegistry, referencedSchemas));
            }
            if (composedSchema.getOneOf() != null) {
                composedSchema.getOneOf().forEach(part -> collectSchemaRefs(part, schemaRegistry, referencedSchemas));
            }
            if (composedSchema.getAnyOf() != null) {
                composedSchema.getAnyOf().forEach(part -> collectSchemaRefs(part, schemaRegistry, referencedSchemas));
            }
        }

        if (schema instanceof ArraySchema arraySchema) {
            collectSchemaRefs(arraySchema.getItems(), schemaRegistry, referencedSchemas);
        }

        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(property -> collectSchemaRefs((Schema<?>) property, schemaRegistry, referencedSchemas));
        }

        Object additionalProperties = schema.getAdditionalProperties();
        if (additionalProperties instanceof Schema<?> additionalSchema) {
            collectSchemaRefs(additionalSchema, schemaRegistry, referencedSchemas);
        }
    }

    @SuppressWarnings("unchecked")
    private Object buildExample(Schema schema,
                                Map<String, Schema> schemaRegistry,
                                Set<String> visitedRefs,
                                int depth) {
        if (schema == null || depth > EXAMPLE_MAX_DEPTH) {
            return null;
        }

        if (schema.getExample() != null) {
            return schema.getExample();
        }

        if (schema.getDefault() != null) {
            return schema.getDefault();
        }

        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return schema.getEnum().get(0);
        }

        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            String key = ref.contains("/") ? ref.substring(ref.lastIndexOf('/') + 1) : ref;
            if (visitedRefs.contains(key)) {
                return Map.of("id", key);
            }

            Schema referenced = schemaRegistry.get(key);
            if (referenced == null) {
                return null;
            }

            Set<String> nextVisited = new LinkedHashSet<>(visitedRefs);
            nextVisited.add(key);
            return buildExample(referenced, schemaRegistry, nextVisited, depth + 1);
        }

        if (schema instanceof ComposedSchema composed) {
            if (composed.getAllOf() != null && !composed.getAllOf().isEmpty()) {
                Map<String, Object> merged = new LinkedHashMap<>();
                for (Schema<?> part : composed.getAllOf()) {
                    Object piece = buildExample(part, schemaRegistry, visitedRefs, depth + 1);
                    if (piece instanceof Map<?, ?> pieceMap) {
                        pieceMap.forEach((k, v) -> merged.put(String.valueOf(k), v));
                    }
                }
                if (!merged.isEmpty()) {
                    return merged;
                }
            }

            if (composed.getOneOf() != null && !composed.getOneOf().isEmpty()) {
                return buildExample(composed.getOneOf().get(0), schemaRegistry, visitedRefs, depth + 1);
            }

            if (composed.getAnyOf() != null && !composed.getAnyOf().isEmpty()) {
                return buildExample(composed.getAnyOf().get(0), schemaRegistry, visitedRefs, depth + 1);
            }
        }

        if (schema instanceof ArraySchema || "array".equals(schema.getType())) {
            Schema<?> itemSchema = schema instanceof ArraySchema
                    ? ((ArraySchema) schema).getItems()
                    : null;

            Object item = buildExample(itemSchema, schemaRegistry, visitedRefs, depth + 1);
            return List.of(item != null ? item : "string");
        }

        if (schema instanceof MapSchema mapSchema) {
            Object additionalProps = mapSchema.getAdditionalProperties();
            if (additionalProps instanceof Schema<?> additionalSchema) {
                Object value = buildExample(additionalSchema, schemaRegistry, visitedRefs, depth + 1);
                return Map.of("key", value != null ? value : "string");
            }
            return Map.of("key", "string");
        }

        if ("object".equals(schema.getType()) || (schema.getProperties() != null && !schema.getProperties().isEmpty())) {
            Map<String, Object> object = new LinkedHashMap<>();
            Map<String, Schema> properties = schema.getProperties();
            List<String> required = schema.getRequired() != null ? schema.getRequired() : List.of();

            if (properties != null && !properties.isEmpty()) {
                required.forEach((name) -> {
                    Schema<?> prop = properties.get(name);
                    Object value = buildExample(prop, schemaRegistry, visitedRefs, depth + 1);
                    object.put(name, value != null ? value : primitiveFallback(prop));
                });

                properties.forEach((name, prop) -> {
                    if (object.containsKey(name)) {
                        return;
                    }
                    Object value = buildExample(prop, schemaRegistry, visitedRefs, depth + 1);
                    object.put(name, value != null ? value : primitiveFallback(prop));
                });
            }

            Object additionalProps = schema.getAdditionalProperties();
            if (additionalProps instanceof Schema<?> additionalSchema) {
                Object value = buildExample(additionalSchema, schemaRegistry, visitedRefs, depth + 1);
                object.putIfAbsent("additionalProp1", value != null ? value : "string");
            }

            if (!object.isEmpty()) {
                return object;
            }
        }

        return primitiveFallback(schema);
    }

    private Object primitiveFallback(Schema<?> schema) {
        if (schema == null) {
            return null;
        }

        String format = schema.getFormat() != null ? schema.getFormat() : "";
        switch (format) {
            case "uuid":
                return "123e4567-e89b-12d3-a456-426614174000";
            case "date-time":
                return "2026-01-01T00:00:00Z";
            case "date":
                return "2026-01-01";
            case "email":
                return "user@example.com";
            case "uri":
            case "url":
                return "https://example.com";
            case "int64":
            case "int32":
                return 1;
            case "float":
            case "double":
                return 1.0;
            case "byte":
            case "binary":
                return "string";
            default:
                break;
        }

        String type = schema.getType();
        if ("integer".equals(type)) {
            return 1;
        }
        if ("number".equals(type)) {
            return 1.0;
        }
        if ("boolean".equals(type)) {
            return true;
        }
        if ("array".equals(type)) {
            return List.of("string");
        }
        if ("object".equals(type)) {
            return Map.of("key", "value");
        }
        return "string";
    }

    private void enrichOperation(Paths paths,
                                 String path,
                                 String method,
                                 String description,
                                 Object success200,
                                 Object success202,
                                 Object failureExample) {
        var operation = operationFor(paths, path, method);
        if (operation == null) {
            return;
        }

        operation.setDescription(description);

        if (success200 != null) {
            addResponseExample(operation, "200", "application/json", success200);
        }
        if (success202 != null) {
            addResponseExample(operation, "202", "application/json", success202);
        }
        if (failureExample != null) {
            addResponseExample(operation, "400", "application/json", failureExample);
            addResponseExample(operation, "401", "application/json", failureExample);
            addResponseExample(operation, "404", "application/json", failureExample);
        }
    }

    private void addResponseExample(io.swagger.v3.oas.models.Operation operation, String statusCode, String contentType, Object example) {
        if (operation.getResponses() == null || !operation.getResponses().containsKey(statusCode)) {
            return;
        }

        ApiResponse response = operation.getResponses().get(statusCode);
        Content content = response.getContent();
        if (content == null) {
            content = new Content();
            response.setContent(content);
        }

        MediaType mediaType = content.get(contentType);
        if (mediaType == null) {
            mediaType = new MediaType();
            content.addMediaType(contentType, mediaType);
        }

        if (mediaType.getExample() == null) {
            mediaType.setExample(example);
        }
    }

    private io.swagger.v3.oas.models.Operation operationFor(Paths paths, String path, String method) {
        PathItem item = paths.get(path);
        if (item == null) {
            return null;
        }

        return switch (method.toUpperCase(Locale.ROOT)) {
            case "GET" -> item.getGet();
            case "POST" -> item.getPost();
            case "PUT" -> item.getPut();
            case "PATCH" -> item.getPatch();
            case "DELETE" -> item.getDelete();
            case "HEAD" -> item.getHead();
            default -> null;
        };
    }

    private String sectionForPath(String path) {
        if (path.startsWith("/api/v1/auth/")) {
            return "Auth & Session";
        }

        if (path.equals("/api/v1/tags")
                || path.startsWith("/api/v1/meta/")
                || path.startsWith("/api/v1/status")
                || path.startsWith("/api/v1/wiki/")
                || path.startsWith("/api/v1/og/")
                || path.equals("/api/v1/analytics/platform/stats")) {
            return "Metadata & System";
        }

        if (path.startsWith("/api/v1/projects")
                || path.startsWith("/api/v1/version/")
                || path.startsWith("/api/v1/download/")
                || path.startsWith("/api/v1/download-bundle/")) {
            return "Projects, Versions & Downloads";
        }

        if (path.startsWith("/api/v1/user/")
                || path.startsWith("/api/v1/users/")
                || path.startsWith("/api/v1/creators/")) {
            return "Users & Profiles";
        }

        if (path.equals("/api/v1/orgs") || path.startsWith("/api/v1/orgs/")) {
            return "Organizations & Connections";
        }

        if (path.equals("/api/v1/notifications") || path.startsWith("/api/v1/notifications/") || path.equals("/api/v1/reports")) {
            return "Notifications & Reports";
        }

        return "Other";
    }

    private boolean isPublicOperation(String path, String method) {
        String normalized = method.toUpperCase(Locale.ROOT);

        if (normalized.equals("POST") && path.equals("/api/v1/users/batch")) {
            return true;
        }

        if (path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/verify")
                || path.equals("/api/v1/auth/signin")
                || path.equals("/api/v1/auth/mfa/validate-login")
                || path.equals("/api/v1/auth/forgot-password")
                || path.equals("/api/v1/auth/reset-password")) {
            return true;
        }

        if (normalized.equals("GET") || normalized.equals("HEAD")) {
            if (path.equals("/api/v1/tags")
                    || path.equals("/api/v1/status")
                    || path.equals("/api/v1/analytics/platform/stats")
                    || path.startsWith("/api/v1/projects/")
                    || path.equals("/api/v1/projects")
                    || path.startsWith("/api/v1/files/")
                    || path.startsWith("/api/v1/user/profile/")
                    || path.startsWith("/api/v1/users/")
                    || path.startsWith("/api/v1/creators/")
                    || path.startsWith("/api/v1/og/")
                    || path.startsWith("/api/v1/download/")
                    || path.startsWith("/api/v1/download-bundle/")
                    || path.startsWith("/api/v1/meta/")
                    || path.startsWith("/api/v1/version/")
                    || path.startsWith("/api/v1/wiki/")) {
                return true;
            }

            if (path.matches("^/api/v1/orgs/[^/]+/members$")) {
                return true;
            }
        }

        return false;
    }

    private List<Map<String, Object>> rateLimitTiers(String path) {
        List<Map<String, Object>> tiers = new ArrayList<>();
        tiers.add(TIER_PUBLIC_IP);
        tiers.add(TIER_STANDARD_API);
        tiers.add(TIER_ENTERPRISE_API);

        return tiers;
    }

    private boolean isAdminOnlyDocPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        if (DOCS_EXCLUDED_EXACT_PATHS.contains(path)) {
            return true;
        }

        if (DOCS_EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }

        return path.matches("^/api/v1/projects/\\{[^/]+}/comments(?:/\\{[^/]+})?(?:/vote)?$")
                || path.matches("^/api/v1/projects/\\{[^/]+}/favorite$")
                || path.matches("^/api/v1/user/(?:follow|unfollow)/\\{[^/]+}$");
    }
}
