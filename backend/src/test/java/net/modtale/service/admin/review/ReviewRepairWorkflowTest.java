package net.modtale.service.admin.review;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewRepairWorkflowTest {
    final ReviewRepairPreparation preparation = mock(ReviewRepairPreparation.class);
    final ReviewIsolationExecutor isolation = mock(ReviewIsolationExecutor.class);
    final ReviewRepairPreparation.Prepared prepared = new ReviewRepairPreparation.Prepared(UUID.randomUUID().toString(), "a".repeat(64), 1000, 2000);
    final ReviewRepairPreparation.Request request = new ReviewRepairPreparation.Request(prepared.id(), "p", 0, "v", prepared.sha256(), "actor", ReviewSnapshotArchive.Action.ISOLATE_REVIEW);

    @Test void stoppedOrUnauthorizedRequestsNeverReachStorage() {
        var workflow = new ReviewRepairWorkflow(preparation, isolation, 1);
        assertThrows(SecurityException.class, () -> workflow.isolate(prepared, "actor", () -> false));
        workflow.close();
        assertThrows(IllegalStateException.class, () -> workflow.prepare(request, () -> true));
        assertEquals(new ReviewRepairWorkflow.Status("CLOSED", 0), workflow.status());
        verifyNoInteractions(preparation, isolation);
    }

    @Test void preparationAndIsolationShareAdmissionWithoutAWaitQueue() throws Exception {
        var workflow = new ReviewRepairWorkflow(preparation, isolation, 1);
        var entered = new CountDownLatch(1);var release = new CountDownLatch(1);
        when(preparation.prepare(eq(request), any())).thenAnswer(i -> {
            entered.countDown();assertTrue(release.await(5, TimeUnit.SECONDS));
            assertTrue(i.<BooleanSupplier>getArgument(1).getAsBoolean());return prepared;
        });
        try (var workers = Executors.newSingleThreadExecutor()) {
            var running = workers.submit(() -> workflow.prepare(request, () -> true));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class, () -> workflow.isolate(prepared, "actor", () -> true));
                assertEquals(new ReviewRepairWorkflow.Status("OPEN", 1), workflow.status());
                verifyNoInteractions(isolation);
                workflow.close();
                assertEquals(new ReviewRepairWorkflow.Status("DRAINING", 1), workflow.status());
            } finally { release.countDown(); }
            assertInstanceOf(IllegalStateException.class, assertThrows(ExecutionException.class, () -> running.get(5, TimeUnit.SECONDS)).getCause());
        }
        assertEquals(new ReviewRepairWorkflow.Status("CLOSED", 0), workflow.status());
    }

    @Test void twoSlotsCanRunButAThirdCannotEnter() throws Exception {
        var workflow = new ReviewRepairWorkflow(preparation, isolation, 2);
        var entered = new CountDownLatch(2);var release = new CountDownLatch(1);
        when(isolation.execute(eq(prepared), eq("actor"), any())).thenAnswer(i -> {
            entered.countDown();assertTrue(release.await(5, TimeUnit.SECONDS));return new ReviewIsolationExecutor.Result("UNKNOWN", null);
        });
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> workflow.isolate(prepared, "actor", () -> true));
            var second = workers.submit(() -> workflow.isolate(prepared, "actor", () -> true));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class, () -> workflow.prepare(request, () -> true));
                assertEquals(2, workflow.status().active());
            } finally { release.countDown(); }
            first.get(5, TimeUnit.SECONDS);second.get(5, TimeUnit.SECONDS);
        }
        assertEquals(0, workflow.status().active());
    }

    @Test void deadlineIsMonotonicAndExpiredWorkReleasesAdmission() {
        var ticks = new AtomicLong(Long.MAX_VALUE - 500000000L);
        var workflow = new ReviewRepairWorkflow(preparation, isolation, 1, 1000, ticks::get);
        when(isolation.execute(eq(prepared), eq("actor"), any())).thenAnswer(i -> {
            assertTrue(i.<BooleanSupplier>getArgument(2).getAsBoolean());
            ticks.addAndGet(1000000000L);
            i.<BooleanSupplier>getArgument(2).getAsBoolean();return null;
        });
        assertThrows(IllegalStateException.class, () -> workflow.isolate(prepared, "actor", () -> true));
        assertEquals(new ReviewRepairWorkflow.Status("OPEN", 0), workflow.status());
    }

    @Test void permissionCheckCannotConsumeDeadlineAndThenAuthorizeWork() {
        var ticks = new AtomicLong();var calls = new AtomicLong();
        var workflow = new ReviewRepairWorkflow(preparation, isolation, 1, 1000, ticks::get);
        when(isolation.execute(eq(prepared), eq("actor"), any())).thenAnswer(i -> {
            i.<BooleanSupplier>getArgument(2).getAsBoolean();return null;
        });
        assertThrows(IllegalStateException.class, () -> workflow.isolate(prepared, "actor", () -> {
            if (calls.incrementAndGet() == 2) ticks.addAndGet(1000000000L);return true;
        }));
        assertEquals(0, workflow.status().active());
    }

    @Test void interruptionBeforeAdmissionDoesNotClearInterruptOrTouchStorage() throws Exception {
        var workflow = new ReviewRepairWorkflow(preparation, isolation, 1);
        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                Thread.currentThread().interrupt();
                try { assertThrows(IllegalStateException.class, () -> workflow.isolate(prepared, "actor", () -> true));assertTrue(Thread.currentThread().isInterrupted()); }
                finally { Thread.interrupted(); }
            }).get(5, TimeUnit.SECONDS);
        }
        verifyNoInteractions(preparation, isolation);
    }

    @Test void committedReceiptIsNotDiscardedByConcurrentShutdown() {
        var workflow = new ReviewRepairWorkflow(preparation, isolation, 1);
        var committed = new ReviewIsolationExecutor.Result("APPLIED", "b".repeat(64));
        when(isolation.execute(eq(prepared), eq("actor"), any())).thenAnswer(i -> { workflow.close();return committed; });
        assertEquals(committed, workflow.isolate(prepared, "actor", () -> true));
        assertEquals(new ReviewRepairWorkflow.Status("CLOSED", 0), workflow.status());
    }

    @Test void exceptionsReleaseSlotsAndLimitsAreValidated() {
        var workflow = new ReviewRepairWorkflow(preparation, isolation, 1);
        when(isolation.execute(eq(prepared), eq("actor"), any())).thenThrow(new IllegalStateException("storage unavailable"));
        assertThrows(IllegalStateException.class, () -> workflow.isolate(prepared, "actor", () -> true));
        assertThrows(IllegalStateException.class, () -> workflow.isolate(prepared, "actor", () -> true));
        verify(isolation, times(2)).execute(eq(prepared), eq("actor"), any());assertEquals(0, workflow.status().active());
        assertThrows(IllegalArgumentException.class, () -> new ReviewRepairWorkflow(preparation, isolation, 3));
        assertThrows(IllegalArgumentException.class, () -> new ReviewRepairWorkflow(preparation, isolation, 1, 999, System::nanoTime));
    }
    @Test void overdueWorkRemainsCountedUntilItActuallyReturns() throws Exception {
        var ticks = new AtomicLong();var entered = new CountDownLatch(1);var release = new CountDownLatch(1);
        var workflow = new ReviewRepairWorkflow(preparation, isolation, 1, 1000, ticks::get);
        when(isolation.execute(eq(prepared), eq("actor"), any())).thenAnswer(i -> {
            entered.countDown();assertTrue(release.await(5, TimeUnit.SECONDS));return new ReviewIsolationExecutor.Result("UNKNOWN", null);
        });
        try (var worker = Executors.newSingleThreadExecutor()) {
            var running = worker.submit(() -> workflow.isolate(prepared, "actor", () -> true));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));ticks.set(1000000000L);
                assertEquals(new ReviewRepairWorkflow.Status("OPEN", 1, 1), workflow.status());
                assertThrows(IllegalStateException.class, () -> workflow.prepare(request, () -> true));
                workflow.close();assertEquals(new ReviewRepairWorkflow.Status("DRAINING", 1, 1), workflow.status());
            } finally { release.countDown(); }
            running.get(5, TimeUnit.SECONDS);
        }
        assertEquals(new ReviewRepairWorkflow.Status("CLOSED", 0, 0), workflow.status());
    }

}
