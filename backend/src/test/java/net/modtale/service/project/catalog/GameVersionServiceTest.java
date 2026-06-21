package net.modtale.service.project.catalog;

import java.util.List;
import net.modtale.config.properties.AppGameVersionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GameVersionServiceTest {

    private static final String HYTALE_RELEASE_METADATA = """
            <metadata>
            <groupId>com.hypixel.hytale</groupId>
            <artifactId>Server</artifactId>
            <versioning>
            <latest>0.5.4</latest>
            <release>0.5.4</release>
            <versions>
            <version>0.5.0</version>
            <version>0.5.1</version>
            <version>0.5.2</version>
            <version>0.5.3</version>
            <version>0.5.4</version>
            </versions>
            <lastUpdated>20260605155755</lastUpdated>
            </versioning>
            </metadata>
            """;

    private static final String HYTALE_PRE_RELEASE_METADATA = """
            <metadata>
            <groupId>com.hypixel.hytale</groupId>
            <artifactId>Server</artifactId>
            <versioning>
            <latest>0.6.0-pre.3</latest>
            <release>0.6.0-pre.3</release>
            <versions>
            <version>0.5.0-pre.9.2</version>
            <version>0.6.0-pre.1</version>
            <version>0.6.0-pre.1.1</version>
            <version>0.6.0-pre.2</version>
            <version>0.6.0-pre.3</version>
            </versions>
            <lastUpdated>20260611141621</lastUpdated>
            </versioning>
            </metadata>
            """;

    @Test
    void indexedVersionsContainingPreAreClassifiedAsPreReleases() {
        GameVersionCatalogOrderingService orderingService = new GameVersionCatalogOrderingService(
                new AppGameVersionProperties("https://versions.example/release.xml", "https://versions.example/pre.xml", 1_000L)
        );

        GameVersionService.GameVersionCatalog catalog = orderingService.buildCatalog(
                new GameVersionCatalogSourceService.GameVersionCatalogSource(
                        List.of("0.5.1"),
                        List.of(),
                        List.of("0.5.0-pre.8", "0.5.0")
                )
        );

        assertEquals(List.of("0.5.1", "0.5.0"), catalog.releaseVersions());
        assertEquals(List.of("0.5.0-pre.8"), catalog.preReleaseVersions());
        assertTrue(catalog.versions().stream()
                .anyMatch(entry -> entry.version().equals("0.5.0-pre.8") && entry.preRelease()));
    }

    @Test
    void initialRefreshSurfacesDownloadFailures() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://versions.example/release.xml")).andRespond(withServerError());

        GameVersionService service = new GameVersionService(
                mockMongoTemplate(),
                new AppGameVersionProperties("https://versions.example/release.xml", "https://versions.example/pre.xml", 1_000L),
                restTemplate
        );

        IllegalStateException error = assertThrows(IllegalStateException.class, service::initialRefresh);
        assertTrue(error.getMessage().contains("Unable to download Maven metadata"));
    }

    @Test
    void laterRefreshFailuresKeepTheLastKnownCatalog() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://versions.example/release.xml"))
                .andRespond(withSuccess(HYTALE_RELEASE_METADATA, MediaType.APPLICATION_XML));
        server.expect(requestTo("https://versions.example/pre.xml"))
                .andRespond(withSuccess(HYTALE_PRE_RELEASE_METADATA, MediaType.APPLICATION_XML));

        GameVersionService service = new GameVersionService(
                mockMongoTemplate(),
                new AppGameVersionProperties("https://versions.example/release.xml", "https://versions.example/pre.xml", 1_000L),
                restTemplate
        );

        service.initialRefresh();
        assertEquals(
                List.of("0.5.4", "0.5.3", "0.5.2", "0.5.1", "0.5.0"),
                service.getCatalog().releaseVersions()
        );
        assertEquals(
                List.of("0.6.0-pre.3", "0.6.0-pre.2", "0.6.0-pre.1.1", "0.6.0-pre.1", "0.5.0-pre.9.2"),
                service.getCatalog().preReleaseVersions()
        );
        assertEquals(
                List.of(
                        "0.6.0-pre.3",
                        "0.6.0-pre.2",
                        "0.6.0-pre.1.1",
                        "0.6.0-pre.1",
                        "0.5.4",
                        "0.5.3",
                        "0.5.2",
                        "0.5.1",
                        "0.5.0",
                        "0.5.0-pre.9.2"
                ),
                service.getCatalog().allVersions()
        );

        server.reset();
        server.expect(requestTo("https://versions.example/release.xml")).andRespond(withServerError());

        service.pollCatalog();

        assertEquals(
                List.of("0.5.4", "0.5.3", "0.5.2", "0.5.1", "0.5.0"),
                service.getCatalog().releaseVersions()
        );
    }

    private static MongoTemplate mockMongoTemplate() {
        return org.mockito.Mockito.mock(MongoTemplate.class);
    }
}
