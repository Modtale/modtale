package net.modtale.service.project.version;

import java.util.List;
import java.util.Optional;
import net.modtale.config.properties.AppCurseForgeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CurseForgeApiClientTest {

    @Test
    void usesTheDocumentedExactFileEndpointPersistsMetadataAndCachesSuccesses() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        CurseForgeApiClient client = new CurseForgeApiClient(properties(), restTemplate);

        server.expect(once(), requestTo("https://api.curseforge.com/v1/mods/search?gameId=1234&slug=simple-compost&pageSize=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", "approved-test-key"))
                .andExpect(header("User-Agent", "Modtale/1.0 (+https://modtale.net)"))
                .andRespond(withSuccess("""
                        {"data":[{"id":1450386,"gameId":1234,"name":"Simple Compost","slug":"simple-compost","summary":"Compost things","isAvailable":true,"allowModDistribution":false,"logo":{"thumbnailUrl":"https://example.test/icon.png"}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.curseforge.com/v1/mods/1450386/files/8227810"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-api-key", "approved-test-key"))
                .andRespond(withSuccess("""
                        {"data":{"id":8227810,"modId":1450386,"isAvailable":true,"displayName":"1.0.0","fileName":"SimpleCompost-1.0.0.jar","releaseType":1,"fileStatus":4,"fileDate":"2026-08-01T00:00:00Z","fileLength":2048,"gameVersions":["2026.08"],"hashes":[{"algo":1,"value":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"algo":2,"value":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"}]}}
                        """, MediaType.APPLICATION_JSON));

        CurseForgeApiClient.CurseForgeProject project = client.resolveProject("simple-compost", "8227810").orElseThrow();

        assertEquals("1450386", project.id());
        assertEquals("Simple Compost", project.title());
        assertEquals(1, project.files().size());
        assertEquals("8227810", project.files().getFirst().id());
        assertEquals("RELEASE", project.files().getFirst().releaseType());
        assertEquals(2048L, project.files().getFirst().fileSize());
        assertEquals("a".repeat(40), project.files().getFirst().hashes().get("sha1"));
        assertEquals("b".repeat(32), project.files().getFirst().hashes().get("md5"));
        assertEquals(List.of("2026.08"), project.files().getFirst().gameVersions());
        assertEquals(false, project.distributionAllowed());

        CurseForgeApiClient.CurseForgeProject cached = client.resolveProject("simple-compost", "8227810").orElseThrow();
        assertEquals(project, cached);
        server.verify();
    }

    @Test
    void listsRecentFilesAndFiltersUnavailableOrMismatchedResults() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        CurseForgeApiClient client = new CurseForgeApiClient(properties(), restTemplate);
        server.expect(requestTo("https://api.curseforge.com/v1/mods/search?gameId=1234&slug=simple-compost&pageSize=1"))
                .andRespond(withSuccess("""
                        {"data":[{"id":1450386,"gameId":1234,"name":"Simple Compost","slug":"simple-compost","isAvailable":true}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.curseforge.com/v1/mods/1450386/files?pageSize=50"))
                .andRespond(withSuccess("""
                        {"data":[
                          {"id":3,"modId":1450386,"isAvailable":true,"displayName":"older","fileDate":"2026-08-01T00:00:00Z"},
                          {"id":4,"modId":1450386,"isAvailable":true,"displayName":"newer","fileDate":"2026-09-01T00:00:00Z"},
                          {"id":5,"modId":1450386,"isAvailable":false,"displayName":"withdrawn"},
                          {"id":6,"modId":999,"isAvailable":true,"displayName":"wrong project"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        CurseForgeApiClient.CurseForgeProject project = client.resolveProject("simple-compost", null).orElseThrow();

        assertEquals(List.of("4", "3"), project.files().stream().map(CurseForgeApiClient.CurseForgeFile::id).toList());
        server.verify();
    }

    @Test
    void rejectsProviderResponsesThatDoNotMatchTheRequestedGameOrSlug() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        CurseForgeApiClient client = new CurseForgeApiClient(properties(), restTemplate);
        server.expect(requestTo("https://api.curseforge.com/v1/mods/search?gameId=1234&slug=simple-compost&pageSize=1"))
                .andRespond(withSuccess("""
                        {"data":[{"id":1450386,"gameId":4321,"name":"Other","slug":"other","isAvailable":true}]}
                        """, MediaType.APPLICATION_JSON));

        assertTrue(client.resolveProject("simple-compost", null).isEmpty());
        server.verify();
    }

    @Test
    void failsClosedToTheStableReferenceFlowWhenProviderIsUnavailable() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        CurseForgeApiClient client = new CurseForgeApiClient(properties(), restTemplate);
        server.expect(requestTo("https://api.curseforge.com/v1/mods/search?gameId=1234&slug=simple-compost&pageSize=1"))
                .andRespond(withResourceNotFound());

        assertTrue(client.resolveProject("simple-compost", null).isEmpty());
        server.verify();
    }

    @Test
    void makesNoRequestWithoutBothAnApprovedKeyAndGameId() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        CurseForgeApiClient client = new CurseForgeApiClient(
                new AppCurseForgeProperties("", 0),
                restTemplate
        );

        Optional<CurseForgeApiClient.CurseForgeProject> result = client.resolveProject("simple-compost", null);

        assertTrue(result.isEmpty());
        server.verify();
    }

    @Test
    void rejectsARequestedFileThatIsMissingOrWithdrawn() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        CurseForgeApiClient client = new CurseForgeApiClient(properties(), restTemplate);
        server.expect(requestTo("https://api.curseforge.com/v1/mods/search?gameId=1234&slug=simple-compost&pageSize=1"))
                .andRespond(withSuccess("""
                        {"data":[{"id":1450386,"gameId":1234,"name":"Simple Compost","slug":"simple-compost","isAvailable":true}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.curseforge.com/v1/mods/1450386/files/8227810"))
                .andRespond(withSuccess("{\"data\":{\"id\":8227810,\"modId\":1450386,\"isAvailable\":false}}", MediaType.APPLICATION_JSON));

        assertTrue(client.resolveProject("simple-compost", "8227810").isEmpty());
        server.verify();
    }

    @Test
    void handlesRateLimitsAndMalformedResponsesWithoutRetryingOrLeakingErrors() {
        RestTemplate throttledTemplate = new RestTemplate();
        MockRestServiceServer throttledServer = MockRestServiceServer.bindTo(throttledTemplate).build();
        CurseForgeApiClient throttledClient = new CurseForgeApiClient(properties(), throttledTemplate);
        throttledServer.expect(once(), requestTo("https://api.curseforge.com/v1/mods/search?gameId=1234&slug=simple-compost&pageSize=1"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "120"));

        assertTrue(throttledClient.resolveProject("simple-compost", null).isEmpty());
        throttledServer.verify();

        RestTemplate malformedTemplate = new RestTemplate();
        MockRestServiceServer malformedServer = MockRestServiceServer.bindTo(malformedTemplate).build();
        CurseForgeApiClient malformedClient = new CurseForgeApiClient(properties(), malformedTemplate);
        malformedServer.expect(once(), requestTo("https://api.curseforge.com/v1/mods/search?gameId=1234&slug=simple-compost&pageSize=1"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertTrue(malformedClient.resolveProject("simple-compost", null).isEmpty());
        malformedServer.verify();
    }

    private static AppCurseForgeProperties properties() {
        return new AppCurseForgeProperties("approved-test-key", 1234);
    }
}
