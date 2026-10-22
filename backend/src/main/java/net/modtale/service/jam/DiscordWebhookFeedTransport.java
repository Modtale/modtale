package net.modtale.service.jam;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class DiscordWebhookFeedTransport implements DiscordFeedTransport {
    private final RestTemplate rest;

    public DiscordWebhookFeedTransport() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.rest = new RestTemplate(factory);
    }

    // A fake HTTP request factory can be injected by tests, without opening sockets.
    DiscordWebhookFeedTransport(RestTemplate rest) { this.rest = rest; }

    @Override
    public Result send(URI destination, Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            var response = rest.postForEntity(destination, new HttpEntity<>(payload, headers), String.class);
            return new Result(response.getStatusCode().value(), retryAfter(response.getHeaders()));
        } catch (HttpStatusCodeException exception) {
            // Return only status and retry timing. Exceptions may contain the secret URL.
            return new Result(exception.getStatusCode().value(), retryAfter(exception.getResponseHeaders()));
        }
    }

    static Duration retryAfter(HttpHeaders headers) {
        if (headers == null) return Duration.ZERO;
        String value = headers.getFirst("Retry-After");
        if (value == null) value = headers.getFirst("X-RateLimit-Reset-After");
        try {
            if (value == null) return Duration.ZERO;
            long milliseconds = new BigDecimal(value).multiply(BigDecimal.valueOf(1000))
                    .setScale(0, java.math.RoundingMode.CEILING).longValueExact();
            return milliseconds > 0 ? Duration.ofMillis(milliseconds) : Duration.ZERO;
        } catch (NumberFormatException | ArithmeticException ignored) {
            return Duration.ZERO;
        }
    }
}
