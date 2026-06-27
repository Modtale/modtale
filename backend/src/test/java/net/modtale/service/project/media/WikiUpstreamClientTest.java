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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WikiUpstreamClientTest {

    @Test
    void cachesProjectPayloadsThroughSpringProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            WikiUpstreamClient client = context.getBean(WikiUpstreamClient.class);
            MockRestServiceServer server = MockRestServiceServer.createServer(context.getBean(RestTemplate.class));

            server.expect(once(), requestTo("https://wiki.modtale.test/api/mods/sky-tools"))
                    .andRespond(withSuccess("{\"pages\":[]}", MediaType.APPLICATION_JSON));

            assertEquals("{\"pages\":[]}", client.fetchProjectPayload("sky-tools"));
            assertEquals("{\"pages\":[]}", client.fetchProjectPayload("sky-tools"));

            server.verify();
        }
    }

    @Test
    void cachesPagePayloadsThroughSpringProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            WikiUpstreamClient client = context.getBean(WikiUpstreamClient.class);
            MockRestServiceServer server = MockRestServiceServer.createServer(context.getBean(RestTemplate.class));

            server.expect(once(), requestTo("https://wiki.modtale.test/api/mods/wiki-1/home-1"))
                    .andRespond(withSuccess("{\"title\":\"Home\"}", MediaType.APPLICATION_JSON));

            assertEquals("{\"title\":\"Home\"}", client.fetchPagePayload("wiki-1", "home-1"));
            assertEquals("{\"title\":\"Home\"}", client.fetchPagePayload("wiki-1", "home-1"));

            server.verify();
        }
    }

    @Test
    void encodesNestedPagePathsWithoutFetchingTheModsList() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            WikiUpstreamClient client = context.getBean(WikiUpstreamClient.class);
            MockRestServiceServer server = MockRestServiceServer.createServer(context.getBean(RestTemplate.class));

            server.expect(once(), requestTo("https://wiki.modtale.test/api/mods/wiki-1/guides/getting-started"))
                    .andRespond(withSuccess("{\"title\":\"Getting Started\"}", MediaType.APPLICATION_JSON));

            assertEquals("{\"title\":\"Getting Started\"}", client.fetchPagePayload("wiki-1", "guides/getting-started"));

            server.verify();
        }
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                    "wikiProjectPayload",
                    "wikiPagePayload"
            );
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
                AppWikiProperties appWikiProperties,
                RestTemplate wikiRestTemplate
        ) {
            return new WikiUpstreamClient(appWikiProperties, wikiRestTemplate);
        }
    }
}
