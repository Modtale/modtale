package net.modtale.service.security.scan;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class BoundedReviewSchedulerTest {
    @ParameterizedTest @CsvSource({"RETRY,1","UNKNOWN,1","exception,1","RETRY,2","UNKNOWN,2","exception,2"})
    void failedCandidateDoesNotDelayHealthyBufferedWork(String failure,int workers)throws Exception {
        var failedCalls=new AtomicInteger();var healthy=new CountDownLatch(1);
        try(var scheduler=new BoundedReviewScheduler<String,Integer>("fair-retry",
                (cursor,limit)->new BoundedReviewScheduler.Page<>(List.of("failed","healthy"),null),
                (candidate,running)->{
                    if(candidate.equals("healthy")){healthy.countDown();return "RECORDED";}
                    failedCalls.incrementAndGet();
                    if(failure.equals("exception"))throw new IllegalStateException("fixture unavailable");
                    return failure;
                },new BoundedReviewScheduler.Settings(workers,2,100,1000))) {
            scheduler.start();
            assertTrue(healthy.await(2,TimeUnit.SECONDS),"One failed candidate must not pause unrelated buffered work");
            Thread.sleep(250);
            assertEquals(1,failedCalls.get(),"Rediscovery must preserve the failed candidate's cooldown");
            assertEquals(1,scheduler.status().failures());
        }
    }
    @Test void failedCandidateBecomesEligibleAtCooldownBoundary()throws Exception {
        var time=new AtomicLong();var calls=new AtomicInteger();var first=new CountDownLatch(1);var retried=new CountDownLatch(1);
        try(var scheduler=new BoundedReviewScheduler<String,Integer>("retry-boundary",
                (cursor,limit)->new BoundedReviewScheduler.Page<>(calls.get()<2?List.of("failed"):List.of(),null),
                (candidate,running)->{if(calls.incrementAndGet()==1){first.countDown();return "RETRY";}retried.countDown();return "RECORDED";},
                new BoundedReviewScheduler.Settings(1,1,100,1000),time::get)) {
            scheduler.start();assertTrue(first.await(2,TimeUnit.SECONDS));
            // Wait for the first outcome to install its cooldown before moving the clock.
            until(()->scheduler.status().processed()==1 && scheduler.status().active()==0);
            time.set(TimeUnit.MILLISECONDS.toNanos(4999));assertFalse(retried.await(250,TimeUnit.MILLISECONDS));
            time.set(TimeUnit.SECONDS.toNanos(5));assertTrue(retried.await(2,TimeUnit.SECONDS));assertEquals(2,calls.get());
        }
    }
    @Test void cooldownTrackingSaturatesWithoutForgettingFailedCandidates()throws Exception {
        var time=new AtomicLong();var discovered=new AtomicInteger();var failures=new AtomicInteger();var saturated=new CountDownLatch(1);var healthy=new CountDownLatch(1);
        try(var scheduler=new BoundedReviewScheduler<Integer,Integer>("bounded-retries",
                (cursor,limit)->new BoundedReviewScheduler.Page<>(List.of(discovered.incrementAndGet()),null),
                (candidate,running)->{if(candidate<=3){failures.incrementAndGet();if(candidate==3)saturated.countDown();return "UNKNOWN";}healthy.countDown();return "RECORDED";},
                new BoundedReviewScheduler.Settings(1,1,100,1000),time::get)) {
            scheduler.start();assertTrue(saturated.await(2,TimeUnit.SECONDS));
            until(()->scheduler.status().processed()==3 && scheduler.status().active()==0);
            assertFalse(healthy.await(250,TimeUnit.MILLISECONDS));assertEquals(3,discovered.get());assertEquals(3,failures.get());
            time.set(TimeUnit.SECONDS.toNanos(5));assertTrue(healthy.await(2,TimeUnit.SECONDS));
        }
    }
    void until(java.util.function.BooleanSupplier ready)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(!ready.getAsBoolean() && System.nanoTime()<end)Thread.sleep(10);
        assertTrue(ready.getAsBoolean());
    }
}
