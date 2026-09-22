package net.modtale.launcher.http;

import static org.junit.jupiter.api.Assertions.*;
import java.net.http.HttpHeaders;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RateLimitHandlerTest {
    @Test
    void parsesSecondsDatesAndOverflowWithoutWrapping() {
        Instant now = Instant.parse("2026-09-16T00:00:00Z");
        assertEquals(7000, RateLimitHandler.parseDelay(" 7 ", now));
        assertEquals(7000, RateLimitHandler.parseDelay(DateTimeFormatter.RFC_1123_DATE_TIME.format(
                now.plusSeconds(7).atZone(ZoneOffset.UTC)), now));
        assertEquals(Long.MAX_VALUE, RateLimitHandler.parseDelay(Long.toString(Long.MAX_VALUE), now));
        for (String invalid : List.of("-1", "invalid", "", "Tue, 15 Sep 2026 00:00:00 GMT")) {
            assertEquals(0, RateLimitHandler.parseDelay(invalid, now));
        }
    }

    @Test
    void isolatesScopesExpiresAndDoesNotShortenExistingCooldown() {
        var clock = new ResponseCacheTest.MutableClock();
        var limits = new RateLimitHandler<String>(clock, Duration.ofMinutes(1));
        assertEquals(7000, limits.record("a", headers("7")));
        clock.now = clock.now.plusSeconds(2);
        assertEquals(5000, limits.remainingMillis("a"));
        assertEquals(0, limits.remainingMillis("b"));
        assertEquals(5000, limits.record("a", headers("1")));
        clock.now = clock.now.plusSeconds(5);
        assertEquals(0, limits.remainingMillis("a"));
        assertEquals(60000, limits.record("a", headers("bad")));
    }

    @Test
    void supportsProviderSpecificHeaderPolicy() {
        var limits = new RateLimitHandler<String>(Clock.systemUTC(), Duration.ofSeconds(1),
                (headers, now) -> 12000L);
        assertTrue(limits.record("provider", headers("1")) > 11000);
    }

    private static HttpHeaders headers(String value) {
        return HttpHeaders.of(Map.of("Retry-After", List.of(value)), (name, entry) -> true);
    }
}
