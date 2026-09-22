package net.modtale.launcher.http;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResponseCacheTest {
    @Test
    void expiresAtBoundaryButRetainsValueForLongerStalePolicy() {
        var clock = new MutableClock();
        var cache = new ResponseCache<String, String>(2, clock);
        cache.put("key", "body");
        clock.now = clock.now.plusSeconds(10);
        assertTrue(cache.get("key", Duration.ofSeconds(10)).isEmpty());
        assertEquals("body", cache.get("key", Duration.ofMinutes(1)).orElseThrow());
        assertTrue(cache.get("key", Duration.ZERO).isEmpty());
        clock.now = Instant.EPOCH.minusSeconds(1);
        assertTrue(cache.get("key", Duration.ofMinutes(1)).isEmpty());
    }

    @Test
    void evictsLeastRecentlyUsedAndKeepsScopesSeparate() {
        var cache = new ResponseCache<String, String>(2, Clock.systemUTC());
        cache.put("account-a", "a");
        cache.put("account-b", "b");
        assertEquals("a", cache.get("account-a", Duration.ofMinutes(1)).orElseThrow());
        cache.put("account-c", "c");
        assertTrue(cache.get("account-b", Duration.ofMinutes(1)).isEmpty());
        cache.invalidate("account-a");
        assertTrue(cache.get("account-a", Duration.ofMinutes(1)).isEmpty());
        cache.clear();
        assertTrue(cache.get("account-c", Duration.ofMinutes(1)).isEmpty());
    }

    @Test
    void coalescesConcurrentCacheMisses() throws Exception {
        var cache = new ResponseCache<String, String>(2, Clock.systemUTC());
        var loads = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = new ArrayList<Future<String>>();
            for (int i = 0; i < 16; i++) results.add(executor.submit(() -> {
                start.await();
                return cache.withRequestLock("key", () -> cache.get("key", Duration.ofMinutes(1)).orElseGet(() -> {
                    loads.incrementAndGet();
                    cache.put("key", "body");
                    return "body";
                }));
            }));
            start.countDown();
            for (var result : results) assertEquals("body", result.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, loads.get());
    }

    static final class MutableClock extends Clock {
        Instant now = Instant.EPOCH;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
