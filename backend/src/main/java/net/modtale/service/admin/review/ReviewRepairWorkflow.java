package net.modtale.service.admin.review;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Shared, fail-fast admission for preparation and isolation; owns no background workers. */
public final class ReviewRepairWorkflow implements AutoCloseable {
    public record Status(String state, int active) {}
    private final ReviewRepairPreparation preparation;
    private final ReviewIsolationExecutor isolation;
    private final int concurrency;
    private final long durationNanos;
    private final LongSupplier ticker;
    private boolean accepting = true;
    private int active;

    public ReviewRepairWorkflow(ReviewRepairPreparation preparation, ReviewIsolationExecutor isolation, int concurrency) {
        this(preparation, isolation, concurrency, 30000, System::nanoTime);
    }

    ReviewRepairWorkflow(ReviewRepairPreparation preparation, ReviewIsolationExecutor isolation, int concurrency,
                         long durationMillis, LongSupplier ticker) {
        if (concurrency < 1 || concurrency > 2 || durationMillis < 1000 || durationMillis > 120000)
            throw new IllegalArgumentException("Invalid repair workflow limits");
        this.preparation = Objects.requireNonNull(preparation);
        this.isolation = Objects.requireNonNull(isolation);
        this.concurrency = concurrency;
        this.durationNanos = TimeUnit.MILLISECONDS.toNanos(durationMillis);
        this.ticker = Objects.requireNonNull(ticker);
    }

    public ReviewRepairPreparation.Prepared prepare(ReviewRepairPreparation.Request request, BooleanSupplier permitted) {
        Objects.requireNonNull(request);
        try (var admission = admit(permitted)) {
            return preparation.prepare(request, admission::permitted);
        }
    }

    public ReviewIsolationExecutor.Result isolate(ReviewRepairPreparation.Prepared prepared, String actor, BooleanSupplier permitted) {
        Objects.requireNonNull(prepared);
        Objects.requireNonNull(actor);
        try (var admission = admit(permitted)) {
            return isolation.execute(prepared, actor, admission::permitted);
        }
    }

    private Admission admit(BooleanSupplier permitted) {
        Objects.requireNonNull(permitted);
        if (!permitted.getAsBoolean()) throw new SecurityException("Review repair is not permitted");
        synchronized (this) {
            if (!accepting || Thread.currentThread().isInterrupted()) throw new IllegalStateException("Review repair is stopped");
            if (active >= concurrency) throw new IllegalStateException("Review repair is busy");
            var admission = new Admission(permitted, ticker.getAsLong());
            active++;
            return admission;
        }
    }

    public synchronized Status status() {
        return new Status(accepting ? "OPEN" : active == 0 ? "CLOSED" : "DRAINING", active);
    }

    // In-flight calls retain their slots until they actually return. A commit already entered may complete.
    @Override public synchronized void close() { accepting = false; }

    private final class Admission implements AutoCloseable {
        private final BooleanSupplier permission;
        private final long started;
        private boolean released;
        Admission(BooleanSupplier permission, long started) { this.permission = permission; this.started = started; }
        boolean permitted() {
            checkpoint();
            boolean allowed = permission.getAsBoolean();
            checkpoint();
            return allowed;
        }
        private void checkpoint() {
            synchronized (ReviewRepairWorkflow.this) {
                if (released || !accepting || Thread.currentThread().isInterrupted()) throw new IllegalStateException("Review repair is stopped");
                if (ticker.getAsLong() - started >= durationNanos) throw new IllegalStateException("Review repair deadline exceeded");
            }
        }
        @Override public void close() {
            synchronized (ReviewRepairWorkflow.this) {
                if (!released) { released = true; active--; }
            }
        }
    }
}
