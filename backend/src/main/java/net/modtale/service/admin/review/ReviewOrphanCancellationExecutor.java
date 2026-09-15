package net.modtale.service.admin.review;

import net.modtale.service.security.scan.RemoteReviewClient;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Bounded one-shot cancellation. Observed remote state never authorizes approval or replacement. */
public final class ReviewOrphanCancellationExecutor implements AutoCloseable {
    public record Execution(String state,ReviewOrphanCancellationJournal.Receipt receipt) {}
    private final ReviewOrphanCancellationJournal journal;
    private final RemoteReviewClient client;
    private final Semaphore slots;
    private final long budgetNanos;
    private final AtomicBoolean closed=new AtomicBoolean();
    public ReviewOrphanCancellationExecutor(ReviewOrphanCancellationJournal journal,RemoteReviewClient client,int concurrency,Duration budget) {
        if(concurrency<1 || concurrency>2 || budget==null || budget.compareTo(Duration.ofSeconds(1))<0 || budget.compareTo(Duration.ofSeconds(30))>0)throw new IllegalArgumentException("Invalid cancellation capacity");
        this.journal=Objects.requireNonNull(journal);this.client=Objects.requireNonNull(client);slots=new Semaphore(concurrency);budgetNanos=budget.toNanos();
    }
    public Execution execute(ReviewOrphanCancellationJournal.Prepared prepared,String actor,BooleanSupplier permitted) {
        if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Cancellation is not permitted");
        if(closed.get())return new Execution("SHUTDOWN",null);
        if(!slots.tryAcquire())return new Execution("BUSY",null);
        long started=System.nanoTime();var deadline=new AtomicLong(started+budgetNanos);
        LongSupplier remaining=()->Math.min(budgetNanos-(System.nanoTime()-started),deadline.get()-System.nanoTime());
        BooleanSupplier allowed=()->{
            if(closed.get() || Thread.currentThread().isInterrupted() || remaining.getAsLong()<=0)throw new IllegalStateException("Cancellation dispatch stopped");
            return permitted.getAsBoolean();
        };
        ReviewOrphanCancellationJournal.Claim claim=null;
        try(var io=ReviewRepairIo.open(remaining)) {
            claim=journal.claim(prepared,actor,allowed);
            if(claim==null)return result(journal.receipt(prepared,actor,allowed));
            var own=claim;ReviewOrphanCancellationJournal.Observation observation;
            try {
                var status=client.cancel(own.target().binding(),()->{
                    long gateStarted=System.nanoTime();long millis=journal.beginDispatch(own,actor,allowed);
                    if(millis<=0)return false;
                    deadline.accumulateAndGet(gateStarted+TimeUnit.MILLISECONDS.toNanos(millis),Math::min);
                    return allowed.getAsBoolean();
                },remaining);
                observation=new ReviewOrphanCancellationJournal.Observation("REMOTE_STATUS",status,null);
            }catch(RemoteReviewClient.Unavailable failure) {
                int code=failure.status();String kind=code==404?"NOT_FOUND":code==409?"CONTEXT_CONFLICT":code==401||code==403||code==429?"UNAVAILABLE":"UNKNOWN";
                observation=new ReviewOrphanCancellationJournal.Observation(kind,null,code);
            }catch(RemoteReviewClient.Superseded stopped) {observation=new ReviewOrphanCancellationJournal.Observation("UNKNOWN",null,null);}
            // Only conservative observation persistence/read-back may use the independent cleanup budget.
            try(var cleanup=ReviewRepairIo.cleanup()){return result(journal.record(own,actor,observation,permitted));}
        }catch(SecurityException denied){throw denied;}
        catch(RuntimeException unknown) {
            try(var cleanup=ReviewRepairIo.cleanup()){return result(journal.receipt(prepared,actor,permitted));}
            catch(SecurityException denied){throw denied;}
            catch(RuntimeException unavailable){return new Execution("UNKNOWN",null);}
        }finally{slots.release();}
    }
    private static Execution result(ReviewOrphanCancellationJournal.Receipt receipt){return new Execution(receipt.state(),receipt);}
    @Override public void close(){closed.set(true);}
}
