package net.modtale.service.project.media;

import net.modtale.config.properties.AppWikiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WikiUpstreamClientTest {

    @Test
    void cachesResolvedWikiIdsThroughSpringProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            WikiUpstreamClient client = context.getBean(WikiUpstreamClient.class);
            MockRestServiceServer server = MockRestServiceServer.createServer(context.getBean(RestTemplate.class));

            server.expect(once(), requestTo("https://wiki.modtale.test/api/mods"))
                    .andRespond(withSuccess("{\"data\":[{\"slug\":\"sky-tools\",\"id\":\"wiki-1\"}]}", MediaType.APPLICATION_JSON));

            assertEquals("wiki-1", client.resolveWikiModId("sky-tools"));
            assertEquals("wiki-1", client.resolveWikiModId("sky-tools"));

            server.verify();
        }
    }

    @Test
    void cachesProjectPayloadsThroughSpringProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            WikiUpstreamClient client = context.getBean(WikiUpstreamClient.class);
            MockRestServiceServer server = MockRestServiceServer.createServer(context.getBean(RestTemplate.class));

            server.expect(once(), requestTo("https://wiki.modtale.test/api/mods/wiki-1"))
                    .andRespond(withSuccess("{\"pages\":[]}", MediaType.APPLICATION_JSON));

            assertEquals("{\"pages\":[]}", client.fetchProjectPayload("wiki-1"));
            assertEquals("{\"pages\":[]}", client.fetchProjectPayload("wiki-1"));

            server.verify();
        }
    }

    @Test
    void reusesTheModsListPayloadAcrossDifferentSlugLookups() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            WikiUpstreamClient client = context.getBean(WikiUpstreamClient.class);
            MockRestServiceServer server = MockRestServiceServer.createServer(context.getBean(RestTemplate.class));

            server.expect(once(), requestTo("https://wiki.modtale.test/api/mods"))
                    .andRespond(withSuccess("{\"data\":[{\"slug\":\"sky-tools\",\"id\":\"wiki-1\"},{\"slug\":\"stone-tools\",\"id\":\"wiki-2\"}]}", MediaType.APPLICATION_JSON));

            assertEquals("wiki-1", client.resolveWikiModId("sky-tools"));
            assertEquals("wiki-2", client.resolveWikiModId("stone-tools"));

            server.verify();
        }
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                    "wikiSlugToId",
                    "wikiModsPayload",
                    "wikiProjectPayload",
                    "wikiPagePayload"
            );
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AppWikiProperties appWikiProperties() {
            return new AppWikiProperties("", "https://wiki.modtale.test/api");
        }

        @Bean
        RestTemplate wikiRestTemplate() {
            return new RestTemplate();
        }

        @Bean
        WikiUpstreamClient wikiUpstreamClient(
                ObjectMapper objectMapper,
                AppWikiProperties appWikiProperties,
                RestTemplate wikiRestTemplate,
                CacheManager cacheManager
        ) {
            return new WikiUpstreamClient(objectMapper, appWikiProperties, wikiRestTemplate, cacheManager);
        }
    }
}
