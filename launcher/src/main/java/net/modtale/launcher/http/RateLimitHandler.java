package net.modtale.launcher.http;

import java.net.http.HttpHeaders;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToLongBiFunction;

public final class RateLimitHandler<K> {
    private final Clock clock;
    private final long fallbackMillis;
    private final ToLongBiFunction<HttpHeaders, Instant> delayParser;
    private final Map<K, Long> deadlines = new HashMap<>();

    public RateLimitHandler(Clock clock, Duration fallback) {
        this(clock, fallback, RateLimitHandler::retryAfterMillis);
    }

    public RateLimitHandler(Clock clock, Duration fallback, ToLongBiFunction<HttpHeaders, Instant> delayParser) {
        this.clock = Objects.requireNonNull(clock);
        this.fallbackMillis = fallback.toMillis();
        if (fallbackMillis <= 0) throw new IllegalArgumentException("Fallback delay must be positive");
        this.delayParser = Objects.requireNonNull(delayParser);
    }

    public synchronized long remainingMillis(K scope) {
        long now = clock.millis();
        deadlines.values().removeIf(deadline -> deadline <= now);
        try {
            return Math.max(0, Math.subtractExact(deadlines.getOrDefault(scope, now), now));
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    public synchronized long record(K scope, HttpHeaders headers) {
        remainingMillis(scope);
        long delay = delayParser.applyAsLong(headers, clock.instant());
        if (delay <= 0) delay = fallbackMillis;
        long deadline;
        try {
            deadline = Math.addExact(clock.millis(), delay);
        } catch (ArithmeticException ex) {
            deadline = Long.MAX_VALUE;
        }
        deadlines.merge(scope, deadline, Math::max);
        return remainingMillis(scope);
    }

    public static long retryAfterMillis(HttpHeaders headers, Instant now) {
        return headers == null ? 0 : parseDelay(headers.firstValue("Retry-After").orElse(""), now);
    }

    public static long parseDelay(String value, Instant now) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Math.max(0, Math.multiplyExact(Long.parseLong(value.trim()), 1000));
        } catch (ArithmeticException ex) {
            return value.trim().startsWith("-") ? 0 : Long.MAX_VALUE;
        } catch (NumberFormatException ignored) {
            try {
                Instant date = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return Math.max(0, Duration.between(now, date).toMillis());
            } catch (DateTimeException | ArithmeticException ex) {
                return 0;
            }
        }
    }
}
