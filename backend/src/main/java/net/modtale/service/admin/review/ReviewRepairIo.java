package net.modtale.service.admin.review;

import com.mongodb.ClientSessionOptions;
import com.mongodb.TransactionOptions;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Synchronous workflow scope; never inherited by another thread or reused after return. */
final class ReviewRepairIo implements AutoCloseable {
    private static final ThreadLocal<ReviewRepairIo> CURRENT = new ThreadLocal<>();
    private final ReviewRepairIo previous;
    private final LongSupplier remainingNanos;
    private boolean closed;

    private ReviewRepairIo(LongSupplier remainingNanos) {
        this.remainingNanos = remainingNanos;
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    static ReviewRepairIo open(LongSupplier remainingNanos) { return new ReviewRepairIo(remainingNanos); }

    // Only conservative receipt reconciliation/bookkeeping may enter this independent cleanup budget.
    static ReviewRepairIo cleanup() {
        long started = System.nanoTime();
        return open(() -> TimeUnit.SECONDS.toNanos(5) - (System.nanoTime() - started));
    }

    private static long timeoutMillis() {
        long nanos = CURRENT.get().remainingNanos.getAsLong();
        if (nanos <= 0) throw new IllegalStateException("Review repair I/O deadline exceeded");
        // Never round a positive remainder down to zero: the driver interprets zero as unlimited.
        return Math.min(5000, Math.max(1, TimeUnit.NANOSECONDS.toMillis(nanos)));
    }

    static <T> MongoCollection<T> collection(MongoCollection<T> collection) {
        return CURRENT.get() == null ? collection : collection.withTimeout(timeoutMillis(), TimeUnit.MILLISECONDS);
    }

    static <T> MongoCollection<T> collection(MongoCollection<T> collection, com.mongodb.client.ClientSession session) {
        if (session != null && session.hasActiveTransaction()) {
            // The driver forbids changing timeoutMS inside a transaction. Its transaction budget applies.
            if (CURRENT.get() != null) timeoutMillis();
            return collection;
        }
        return collection(collection);
    }

    static MongoDatabase database(MongoDatabase database) {
        return CURRENT.get() == null ? database : database.withTimeout(timeoutMillis(), TimeUnit.MILLISECONDS);
    }

    static ClientSessionOptions sessionOptions() {
        var builder = ClientSessionOptions.builder().causallyConsistent(false);
        if (CURRENT.get() != null) builder.defaultTimeout(timeoutMillis(), TimeUnit.MILLISECONDS);
        return builder.build();
    }

    static TransactionOptions transactionOptions(TransactionOptions base) {
        if (CURRENT.get() == null) return base;
        return TransactionOptions.builder().readConcern(base.getReadConcern()).readPreference(base.getReadPreference())
                .writeConcern(base.getWriteConcern()).timeout(timeoutMillis(), TimeUnit.MILLISECONDS).build();
    }

    @Override public void close() {
        if (closed) return;
        if (CURRENT.get() != this) throw new IllegalStateException("Invalid repair I/O scope");
        closed = true;
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
