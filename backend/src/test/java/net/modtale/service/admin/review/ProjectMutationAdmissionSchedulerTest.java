package net.modtale.service.admin.review;

import net.modtale.service.security.scan.RemoteReviewScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectMutationAdmissionSchedulerTest {
    ProjectMutationDiscovery discovery=mock(ProjectMutationDiscovery.class);ProjectMutationAutomaticAdmission automatic=mock(ProjectMutationAutomaticAdmission.class);
    ProjectMutationAdmissionScheduler scheduler(long drain){return new ProjectMutationAdmissionScheduler(discovery,automatic,new ProjectMutationAdmissionScheduler.Settings(1,4,100,drain));}
    ProjectMutationDiscovery.Candidate candidate(){return new ProjectMutationDiscovery.Candidate("p",0,"v",UUID.randomUUID().toString(),UUID.randomUUID().toString(),1);}
    void empty(){when(discovery.page(any(),anyInt())).thenReturn(new ProjectMutationDiscovery.Page(List.of(),null,0,0));}
    void until(BooleanSupplier ready)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!ready.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(10);assertTrue(ready.getAsBoolean());}
    @Test void constructionDoesNotRunAndClosingPreventsStart(){empty();var scheduler=scheduler(1000);verifyNoInteractions(discovery,automatic);scheduler.close();assertEquals("STOPPED",scheduler.status().state());assertThrows(IllegalStateException.class,scheduler::start);}
    @Test void emptyUnavailablePageContinuesAndReportsMalformedCandidates()throws Exception {
        var cursor=new ProjectMutationDiscovery.Cursor("p",1,false);var candidate=candidate();var handled=new CountDownLatch(1);
        when(discovery.page(any(),anyInt())).thenReturn(new ProjectMutationDiscovery.Page(List.of(),cursor,1,1),new ProjectMutationDiscovery.Page(List.of(candidate),null,1,0),new ProjectMutationDiscovery.Page(List.of(),null,0,0));
        when(automatic.advance(eq(candidate),any())).thenAnswer(call->{handled.countDown();return new ProjectMutationAutomaticAdmission.Result("ADMITTED","decision");});
        try(var scheduler=scheduler(1000)){scheduler.start();assertTrue(handled.await(3,TimeUnit.SECONDS));until(()->scheduler.status().processed()==1);assertEquals(1,scheduler.unavailableCandidates());verify(discovery).page(cursor,4);assertEquals("ADMITTED",scheduler.status().lastOutcome());}
    }
    @Test void stopWaitsForActualAdmissionReturnBeforeCallback()throws Exception {
        var candidate=candidate();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var done=new CountDownLatch(1);var guard=new AtomicReference<BooleanSupplier>();
        when(discovery.page(any(),anyInt())).thenReturn(new ProjectMutationDiscovery.Page(List.of(candidate),null,1,0));
        when(automatic.advance(any(),any())).thenAnswer(call->{guard.set(call.getArgument(1));entered.countDown();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(release.getCount()!=0&&System.nanoTime()<end)try{release.await(50,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){}return new ProjectMutationAutomaticAdmission.Result("RUNNING",null);});
        var scheduler=scheduler(100);scheduler.start();try{assertTrue(entered.await(2,TimeUnit.SECONDS));scheduler.stop(done::countDown);until(()->"DRAIN_TIMEOUT".equals(scheduler.status().state()));assertEquals(1,done.getCount());assertFalse(guard.get().getAsBoolean());assertThrows(IllegalStateException.class,scheduler::close);}finally{release.countDown();}
        assertTrue(done.await(3,TimeUnit.SECONDS));scheduler.close();assertEquals("STOPPED",scheduler.status().state());
    }
    @Test void schedulerRequiresExplicitFlagDependenciesAndStopsWithContext(){
        new ApplicationContextRunner().withUserConfiguration(ProjectMutationAdmissionSchedulerConfiguration.class).run(context->assertFalse(context.containsBean("projectMutationAdmissionScheduler")));
        empty();var recovery=mock(ProjectMutationAttemptRecovery.class);when(recovery.page(any(),anyInt())).thenReturn(new ProjectMutationAttemptRecovery.Page(List.of(),null,0,0));var runner=new ApplicationContextRunner().withUserConfiguration(ProjectMutationAdmissionSchedulerConfiguration.class).withBean(ProjectMutationDiscovery.class,()->discovery)
                .withBean(ProjectMutationAttemptRecovery.class,()->recovery).withBean(ProjectMutationAutomaticAdmission.class,()->automatic).withPropertyValues("app.warden.repair.admission.scheduler.enabled=true");
        runner.run(context->assertNotNull(context.getStartupFailure()));var delivery=mock(RemoteReviewScheduler.class);
        runner.withBean(RemoteReviewScheduler.class,()->delivery).run(context->assertNotNull(context.getStartupFailure()));
        var recovered=new AtomicReference<ProjectMutationRecoveryScheduler>();var reference=new AtomicReference<ProjectMutationAdmissionScheduler>();runner.withBean(RemoteReviewScheduler.class,()->delivery)
                .withPropertyValues("app.warden.repair.enabled=true","app.warden.jobs.enabled=true").run(context->{assertNull(context.getStartupFailure());reference.set(context.getBean(ProjectMutationAdmissionScheduler.class));assertTrue(reference.get().isRunning());recovered.set(context.getBean(ProjectMutationRecoveryScheduler.class));assertTrue(recovered.get().isRunning());});
        assertEquals("STOPPED",reference.get().status().state());assertEquals("STOPPED",recovered.get().status().state());
    }
}
