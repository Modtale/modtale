package net.modtale.config.core;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {

    private final OpenApiConfig openApiConfig = new OpenApiConfig();

    @Test
    void apiDocsCustomizerRemovesAdminAndAbusePronePaths() {
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/admin/users/bans", new PathItem().get(new Operation().operationId("adminUsersBans")))
                .addPathItem("/api/v1/reports", new PathItem().post(new Operation().operationId("submitReport")))
                .addPathItem("/api/v1/projects/{id}/comments", new PathItem().post(new Operation().operationId("addComment")))
                .addPathItem("/api/v1/projects/{id}/comments/{commentId}", new PathItem().put(new Operation().operationId("editComment")))
                .addPathItem("/api/v1/projects/{id}/comments/{commentId}/vote", new PathItem().post(new Operation().operationId("voteComment")))
                .addPathItem("/api/v1/projects", new PathItem().get(new Operation().operationId("listProjects"))));

        openApiConfig.apiDocsOpenApiCustomizer().customise(openApi);

        Paths filteredPaths = openApi.getPaths();
        assertFalse(filteredPaths.containsKey("/api/v1/admin/users/bans"));
        assertFalse(filteredPaths.containsKey("/api/v1/reports"));
        assertFalse(filteredPaths.containsKey("/api/v1/projects/{id}/comments"));
        assertFalse(filteredPaths.containsKey("/api/v1/projects/{id}/comments/{commentId}"));
        assertFalse(filteredPaths.containsKey("/api/v1/projects/{id}/comments/{commentId}/vote"));
        assertTrue(filteredPaths.containsKey("/api/v1/projects"));
    }

    @Test
    void apiDocsCustomizerStillDecoratesAllowedOperations() {
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/api/v1/projects", new PathItem().get(new Operation().operationId("listProjects"))));

        openApiConfig.apiDocsOpenApiCustomizer().customise(openApi);

        Operation operation = openApi.getPaths().get("/api/v1/projects").getGet();
        assertNotNull(operation.getExtensions());
        assertTrue(operation.getExtensions().containsKey("x-modtale-access"));
        assertTrue(operation.getExtensions().containsKey("x-modtale-rate-limit-tiers"));
        assertTrue(operation.getTags().contains("Projects, Versions & Downloads"));
    }

    @Test
    void apiDocsCustomizerPrunesSchemasUsedOnlyByExcludedRoutes() {
        Schema<?> keptSchema = new ObjectSchema();
        Schema<?> sensitiveSchema = new ObjectSchema();

        OpenAPI openApi = new OpenAPI()
                .components(new Components()
                        .addSchemas("KeptSchema", keptSchema)
                        .addSchemas("SensitiveSchema", sensitiveSchema))
                .paths(new Paths()
                        .addPathItem("/api/v1/projects", new PathItem().get(new Operation()
                                .operationId("listProjects")
                                .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                                        .addApiResponse("200", jsonResponse("#/components/schemas/KeptSchema")))))
                        .addPathItem("/api/v1/reports", new PathItem().post(new Operation()
                                .operationId("submitReport")
                                .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                                        .addApiResponse("200", jsonResponse("#/components/schemas/SensitiveSchema"))))));

        openApiConfig.apiDocsOpenApiCustomizer().customise(openApi);

        assertTrue(openApi.getComponents().getSchemas().containsKey("KeptSchema"));
        assertFalse(openApi.getComponents().getSchemas().containsKey("SensitiveSchema"));
    }

    private static ApiResponse jsonResponse(String schemaRef) {
        return new ApiResponse().content(new Content().addMediaType(
                "application/json",
                new MediaType().schema(new Schema<>().$ref(schemaRef))
        ));
    }
}
