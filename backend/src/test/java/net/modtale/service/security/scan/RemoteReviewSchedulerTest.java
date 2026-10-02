package net.modtale.service.security.scan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteReviewSchedulerTest {
    RemoteReviewDiscovery discovery=mock(RemoteReviewDiscovery.class);
    RemoteReviewBootstrap bootstrap=mock(RemoteReviewBootstrap.class);
    RemoteReviewScheduler scheduler(int workers,long drain){return new RemoteReviewScheduler(discovery,bootstrap,new RemoteReviewScheduler.Settings(workers,4,100,drain));}
    RemoteReviewDiscovery.Candidate candidate(String id){return new RemoteReviewDiscovery.Candidate("p",id,1,UUID.randomUUID().toString());}
    void until(BooleanSupplier ready)throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!ready.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(10);assertTrue(ready.getAsBoolean());}
    void empty(){when(discovery.page(any(),anyInt())).thenReturn(new RemoteReviewDiscovery.Page(List.of(),null,0));}
    @Test void constructionDoesNotPollAndClosedSchedulerCannotRestart() {
        empty();var scheduler=scheduler(1,1000);verifyNoInteractions(discovery,bootstrap);scheduler.close();assertEquals("STOPPED",scheduler.status().state());assertFalse(scheduler.isRunning());assertThrows(IllegalStateException.class,scheduler::start);
    }
    @Test void boundedWorkersConsumeAllBufferedCandidatesBeforeAnotherPage()throws Exception {
        var a=candidate("a");var b=candidate("b");var c=candidate("c");var entered=new CountDownLatch(2);var release=new CountDownLatch(1);var invoked=ConcurrentHashMap.<String>newKeySet();
        when(discovery.page(any(),anyInt())).thenReturn(new RemoteReviewDiscovery.Page(List.of(a,b,c),new RemoteReviewDiscovery.Cursor("p",0,true),3),new RemoteReviewDiscovery.Page(List.of(),null,0));
        when(bootstrap.advance(anyString(),anyString(),anyInt(),anyString(),any())).thenAnswer(i->{invoked.add(i.getArgument(1));entered.countDown();release.await(3,TimeUnit.SECONDS);return new RemoteReviewStep.Outcome("RECORDED",null);});
        try(var scheduler=scheduler(2,1000)) {
            scheduler.start();scheduler.start();try{assertTrue(entered.await(2,TimeUnit.SECONDS));assertEquals(2,scheduler.status().active());assertEquals(1,scheduler.status().buffered());verify(discovery,times(1)).page(any(),anyInt());}
            finally{release.countDown();}
            until(()->invoked.size()==3);assertEquals(Set.of("a","b","c"),invoked);
        }
    }
    @Test void duplicateInFlightCandidateIsNotDispatchedTwice()throws Exception {
        var candidate=candidate("v");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(discovery.page(any(),anyInt())).thenReturn(new RemoteReviewDiscovery.Page(List.of(candidate,candidate),null,2),new RemoteReviewDiscovery.Page(List.of(),null,0));
        when(bootstrap.advance(anyString(),anyString(),anyInt(),anyString(),any())).thenAnswer(i->{entered.countDown();release.await(3,TimeUnit.SECONDS);return new RemoteReviewStep.Outcome("RECORDED",null);});
        try(var scheduler=scheduler(2,1000)){scheduler.start();try{assertTrue(entered.await(2,TimeUnit.SECONDS));Thread.sleep(250);verify(bootstrap,times(1)).advance(anyString(),anyString(),anyInt(),anyString(),any());}finally{release.countDown();}}
    }
    @Test void discoveryFailureBacksOffInsteadOfSpinning()throws Exception {
        when(discovery.page(any(),anyInt())).thenThrow(new IllegalStateException("fixture unavailable"));
        try(var scheduler=scheduler(1,1000)){scheduler.start();until(()->scheduler.status().failures()==1);Thread.sleep(250);verify(discovery,times(1)).page(any(),anyInt());assertEquals("POLL_ERROR",scheduler.status().lastOutcome());}
    }
    @Test void timeoutNeverRunsStopCallbacksBeforeUncooperativeWorkEnds()throws Exception {
        var candidate=candidate("v");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var done=new CountDownLatch(2);var guard=new AtomicReference<BooleanSupplier>();
        when(discovery.page(any(),anyInt())).thenReturn(new RemoteReviewDiscovery.Page(List.of(candidate),null,1),new RemoteReviewDiscovery.Page(List.of(),null,0));
        when(bootstrap.advance(anyString(),anyString(),anyInt(),anyString(),any())).thenAnswer(i->{guard.set(i.getArgument(4));entered.countDown();long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(release.getCount()>0&&System.nanoTime()<limit)try{release.await(50,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}
            return new RemoteReviewStep.Outcome("SHUTDOWN",null);});
        var scheduler=scheduler(1,100);scheduler.start();
        try {
            assertTrue(entered.await(2,TimeUnit.SECONDS));scheduler.stop(done::countDown);scheduler.stop(done::countDown);until(()->"DRAIN_TIMEOUT".equals(scheduler.status().state()));
            assertTrue(scheduler.isRunning());assertEquals(2,done.getCount());assertFalse(guard.get().getAsBoolean());
            assertThrows(IllegalStateException.class,scheduler::close);
        }finally{release.countDown();}
        assertTrue(done.await(2,TimeUnit.SECONDS));scheduler.close();assertEquals("STOPPED",scheduler.status().state());
    }
    @Test void invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new RemoteReviewScheduler.Settings(3,4,100,100));assertThrows(IllegalArgumentException.class,()->new RemoteReviewScheduler.Settings(1,65,100,100));
        assertThrows(IllegalArgumentException.class,()->new RemoteReviewScheduler.Settings(1,4,99,100));assertThrows(IllegalArgumentException.class,()->new RemoteReviewScheduler.Settings(1,4,100,30001));
    }
    @Test void springSchedulerIsAbsentByDefault() {
        new ApplicationContextRunner().withUserConfiguration(RemoteReviewSchedulerConfig.class).run(context->assertFalse(context.containsBean("remoteReviewScheduler")));
    }
    @Test void springSchedulerRequiresRemoteModeAndStopsWithContext() {
        empty();var runner=new ApplicationContextRunner().withUserConfiguration(RemoteReviewSchedulerConfig.class).withBean(RemoteReviewDiscovery.class,()->discovery).withBean(RemoteReviewBootstrap.class,()->bootstrap)
                .withPropertyValues("app.warden.jobs.scheduler.enabled=true");
        runner.run(context->assertNotNull(context.getStartupFailure()));
        var reference=new AtomicReference<RemoteReviewScheduler>();runner.withPropertyValues("app.warden.jobs.enabled=true").run(context->{assertNull(context.getStartupFailure());reference.set(context.getBean(RemoteReviewScheduler.class));assertTrue(reference.get().isRunning());});
        assertEquals("STOPPED",reference.get().status().state());
    }
}
