package net.modtale.service.security.scan;

import com.mongodb.WriteConcern;
import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationFairnessTest {
    ProjectMutationObservationProgressTest large=new ProjectMutationObservationProgressTest();
    ProjectMutationAutomaticAdmissionTest small=new ProjectMutationAutomaticAdmissionTest();
    @AfterEach void cleanup(){try{small.cleanup();}finally{large.cleanup();}}
    @Test void smallProjectFinishesBeforeEarlierLongHistoryOnOneWorker()throws Exception{
        large.setup(16);small.f().project="ffffffffffffffffffffffff";small.setup();
        var a=large.base.f();var b=small.f();var first=large.base.candidate();var second=small.candidate();
        for(String name:b.mongo.getCollectionNames()){
            var target=a.mongo.getCollection(name).withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
            try(var rows=b.mongo.getCollection(name).find().iterator()){while(rows.hasNext())target.insertOne(rows.next());}
        }
        var jobsAtSmallCompletion=new AtomicInteger(-1);long started=System.nanoTime();
        try(var budget=new ReviewRepairWorkflow(mock(ReviewRepairPreparation.class),mock(ReviewIsolationExecutor.class),2);
            var p=new ProjectMutationParallelHistoryTest().pipeline(a.mongo,budget,large.base.base.base.base.base.base.archive,a.client);
            var q=new ProjectMutationParallelHistoryTest().pipeline(a.mongo,budget,large.base.base.base.base.base.base.archive,b.client)){
            // Route the two isolated HTTP fixtures to real coordinators; discovery, scheduling and persistence remain real.
            var dispatch=mock(ProjectMutationAutomaticAdmission.class);
            when(dispatch.advance(any(),any())).thenAnswer(call->{
                var candidate=call.getArgument(0,ProjectMutationDiscovery.Candidate.class);var allowed=call.getArgument(1,BooleanSupplier.class);
                assertTrue(candidate.projectId().equals(first.projectId())||candidate.projectId().equals(second.projectId()));
                var result=(candidate.projectId().equals(first.projectId())?p:q).automatic().advance(candidate,allowed);
                if(candidate.projectId().equals(second.projectId())&&"ADMITTED".equals(result.state()))jobsAtSmallCompletion.compareAndSet(-1,large.reads.get());
                return result;
            });
            try(var scheduler=new ProjectMutationAdmissionScheduler(new ProjectMutationDiscovery(a.mongo),dispatch,new ProjectMutationAdmissionScheduler.Settings(1,1,100,10000))){
                scheduler.start();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
                while(System.nanoTime()<end && a.mongo.getCollection(ProjectMutationActivator.ADMISSIONS).countDocuments()<2)Thread.sleep(100);
                assertEquals(2,a.mongo.getCollection(ProjectMutationActivator.ADMISSIONS).countDocuments(),scheduler.status().toString());
                assertEquals(0,scheduler.status().failures());assertEquals(0,scheduler.unavailableCandidates());
            }
        }
        assertTrue(jobsAtSmallCompletion.get()>0 && jobsAtSmallCompletion.get()<16,"jobsBeforeSmall="+jobsAtSmallCompletion.get());
        assertEquals(16,large.reads.get());assertEquals(17,a.mongo.getCollection(ProjectMutationJobAccounting.COLLECTION).countDocuments());
        assertEquals(0,a.posts.get());assertEquals(0,b.posts.get());
        for(var candidate:java.util.List.of(first,second)){
            var ledger=a.mongo.getCollection(ProjectMutationAdmissionAttempts.COLLECTION).find(new Document("_id",candidate.requestId())).first();
            assertEquals("ADMITTED",ledger.getString("state"));assertEquals(candidate.equals(first)?16:1,ledger.getList("attempts",Document.class).size());
        }
        System.out.println("fairnessLongJobs=16 shortJobs=1 longJobsAtShortCompletion="+jobsAtSmallCompletion.get()+" totalMillis="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
    }
}
