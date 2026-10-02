package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_MONGO_URI", matches = ".+")
class RecurringStateConcurrencyIntegrationTest extends FinancePipelineFixture {
    @Test void slowOlderActiveResponseCannotUndoCompletedCancellation() throws Exception { assertLatestWins("canceled", false); }
    @Test void slowOlderResponseCannotRemoveScheduledCancellation() throws Exception { assertLatestWins("active", true); }
    private void assertLatestWins(String latestStatus, boolean cancelAtPeriodEnd) throws Exception {
        var intent = checkout(true); recurring.registerCheckout(intent, session(intent));
        var firstInFlight = new CountDownLatch(1); var releaseFirst = new CountDownLatch(1); var calls = new AtomicInteger();
        when(gateway.getSubscription("sub_synthetic")).thenAnswer(call -> {
            if (calls.incrementAndGet() == 1) {
                firstInFlight.countDown(); assertTrue(releaseFirst.await(10, TimeUnit.SECONDS));
                return providerState("active", false);
            }
            return providerState(latestStatus, cancelAtPeriodEnd);
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var old = executor.submit(() -> { try { recurring.refreshSubscription("sub_synthetic"); return true; } catch (IllegalArgumentException stale) { return false; } });
            try {
                assertTrue(firstInFlight.await(10, TimeUnit.SECONDS)); recurring.refreshSubscription("sub_synthetic");
                var latest = subscriptions.findById("sub_synthetic").orElseThrow(); assertEquals(latestStatus, latest.getStatus()); assertEquals(cancelAtPeriodEnd, latest.isCancelAtPeriodEnd());
            } finally { releaseFirst.countDown(); }
            assertFalse(old.get(10, TimeUnit.SECONDS));
        }
        var persisted = subscriptions.findById("sub_synthetic").orElseThrow(); assertEquals(latestStatus, persisted.getStatus()); assertEquals(cancelAtPeriodEnd, persisted.isCancelAtPeriodEnd());
        recurring.refreshSubscription("sub_synthetic"); assertEquals(0, ledger.count()); assertEquals(0, available());
    }
    @Test void revisionAdvancesPastStoredTimestampEvenWhenWallClockHasNotCaughtUp() {
        var intent = checkout(true); recurring.registerCheckout(intent, session(intent));
        var subscription = subscriptions.findById("sub_synthetic").orElseThrow();
        Instant ahead = Instant.now().plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MILLIS); subscription.setUpdatedAt(ahead); subscriptions.save(subscription);
        when(gateway.getSubscription("sub_synthetic")).thenReturn(providerState("active", true));
        recurring.refreshSubscription("sub_synthetic"); recurring.refreshSubscription("sub_synthetic");
        assertEquals(ahead.plusMillis(2), subscriptions.findById("sub_synthetic").orElseThrow().getUpdatedAt());
    }
    private Map<String, Object> providerState(String status, boolean cancel) {
        return Map.of("id", "sub_synthetic", "customer", "cus_synthetic", "livemode", false, "status", status, "cancel_at_period_end", cancel);
    }
}
