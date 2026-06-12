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
                .andRespond(withSuccess("""
                        <metadata><versioning><versions><version>1.2.0</version></versions></versioning></metadata>
                        """, MediaType.APPLICATION_XML));
        server.expect(requestTo("https://versions.example/pre.xml"))
                .andRespond(withSuccess("""
                        <metadata><versioning><versions><version>1.2.0-pre.1</version></versions></versioning></metadata>
                        """, MediaType.APPLICATION_XML));

        GameVersionService service = new GameVersionService(
                mockMongoTemplate(),
                new AppGameVersionProperties("https://versions.example/release.xml", "https://versions.example/pre.xml", 1_000L),
                restTemplate
        );

        service.initialRefresh();
        assertEquals(List.of("1.2.0"), service.getCatalog().releaseVersions());

        server.reset();
        server.expect(requestTo("https://versions.example/release.xml")).andRespond(withServerError());

        service.pollCatalog();

        assertEquals(List.of("1.2.0"), service.getCatalog().releaseVersions());
    }

    private static MongoTemplate mockMongoTemplate() {
        return org.mockito.Mockito.mock(MongoTemplate.class);
    }
}
