package net.modtale.service.jam;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import net.modtale.config.properties.AppModjamDiscordFeedProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

class DiscordWebhookFeedTransportTest {
    private static final URI FAKE_DESTINATION = URI.create("https://discord.com/api/webhooks/1/test-only-placeholder?wait=true");

    @Test
    void sendsJsonAndWaitsForAcknowledgementUsingOnlyFakeHttp() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo(FAKE_DESTINATION)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(content().json("{\"content\":\"Test\",\"allowed_mentions\":{\"parse\":[]}}"))
                .andRespond(withSuccess("{\"id\":\"fake-message\"}", MediaType.APPLICATION_JSON));
        var result = new DiscordWebhookFeedTransport(rest).send(FAKE_DESTINATION,
                Map.of("content", "Test", "allowed_mentions", Map.of("parse", List.of())));
        assertTrue(result.accepted());
        server.verify();
    }

    @Test
    void rateLimitUsesFractionalSecondsFromRetryAfterWithoutLeakingException() {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo(FAKE_DESTINATION)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "12.345").body("{\"retry_after\":12.345}"));
        var result = new DiscordWebhookFeedTransport(rest).send(FAKE_DESTINATION, Map.of("content", "Test"));
        assertEquals(429, result.status());
        assertEquals(Duration.ofMillis(12345), result.retryAfter());
        assertFalse(result.accepted());
        server.verify();
    }

    @Test
    void malformedRetryHeadersUseSafeBackoffAndResetAfterIsSupported() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "invalid");
        assertEquals(Duration.ZERO, DiscordWebhookFeedTransport.retryAfter(headers));
        headers.set("Retry-After", "-3");
        assertEquals(Duration.ZERO, DiscordWebhookFeedTransport.retryAfter(headers));
        headers.remove("Retry-After");
        headers.set("X-RateLimit-Reset-After", "0.0012");
        assertEquals(Duration.ofMillis(2), DiscordWebhookFeedTransport.retryAfter(headers));
    }

    @Test
    void defaultTransportHasBoundedConnectionAndReadTimeoutsWithoutSending() {
        var transport = new DiscordWebhookFeedTransport();
        RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(transport, "rest");
        assertNotNull(rest);
        assertEquals(3000, ReflectionTestUtils.getField(rest.getRequestFactory(), "connectTimeout"));
        assertEquals(10000, ReflectionTestUtils.getField(rest.getRequestFactory(), "readTimeout"));
    }

    @Test
    void propertyDefaultsAreDisabledAndSecretIsNotIncludedInToString() {
        var binder = new Binder(new MapConfigurationPropertySource(
                Map.of("app.modjam-discord-feed.url", FAKE_DESTINATION.toString())));
        var properties = binder.bind("app.modjam-discord-feed", AppModjamDiscordFeedProperties.class)
                .orElseThrow(AssertionError::new);
        assertFalse(properties.enabled());
        assertFalse(properties.toString().contains("test-only-placeholder"));
    }
}
