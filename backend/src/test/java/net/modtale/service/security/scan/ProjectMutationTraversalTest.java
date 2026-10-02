package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationTraversalTest {
    ProjectMutationObservationProgressTest fixture=new ProjectMutationObservationProgressTest();
    @AfterEach void cleanup(){fixture.cleanup();}
    @Test void maximumJobInventoryTraversesRealBackgroundGenerationsWithoutRepeatedReads()throws Exception{
        fixture.setup(255);var base=fixture.base;var f=base.f();var candidate=base.candidate();
        var archive=base.base.base.base.base.base.archive;
        long started=System.nanoTime();
        try(var budget=new ReviewRepairWorkflow(mock(ReviewRepairPreparation.class),mock(ReviewIsolationExecutor.class),2);
            var pipeline=new ProjectMutationParallelHistoryTest().pipeline(f.mongo,budget,archive,f.client);
            var scheduler=new ProjectMutationAdmissionScheduler(new ProjectMutationDiscovery(f.mongo),pipeline.automatic(),new ProjectMutationAdmissionScheduler.Settings(1,4,100,10000))){
            scheduler.start();long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(10);String state=null;
            while(System.nanoTime()<deadline){
                var record=f.mongo.getCollection(ProjectMutationAdmissionAttempts.COLLECTION).find(new Document("_id",candidate.requestId())).projection(new Document("state",1)).first();
                state=record==null?null:record.getString("state");
                if("ADMITTED".equals(state)||"ATTENTION".equals(state))break;
                Thread.sleep(100);
            }
            assertEquals("ADMITTED",state,"reads="+fixture.reads.get()+" scheduler="+scheduler.status());
            assertEquals(0,scheduler.status().failures());assertEquals(0,scheduler.unavailableCandidates());
        }
        assertEquals(255,fixture.reads.get());assertEquals(255,f.mongo.getCollection(ProjectMutationObservationProgressTest.PROGRESS).countDocuments());assertEquals(0,f.posts.get());
        var ledger=f.mongo.getCollection(ProjectMutationAdmissionAttempts.COLLECTION).find(new Document("_id",candidate.requestId())).first();
        var attempts=ledger.getList("attempts",Document.class);assertEquals(255,attempts.size());
        for(int i=0;i<254;i++)assertEquals("YIELDED",attempts.get(i).getString("outcome"));assertEquals("ADMITTED",attempts.getLast().getString("outcome"));
        var admission=f.mongo.getCollection(ProjectMutationActivator.ADMISSIONS).find(new Document("_id",candidate.requestId())).first();assertNotNull(admission);
        var payload=new RawBsonDocument(archive.load(admission.getString("decisionId")).versionBytes()).decode(new DocumentCodec());
        assertEquals(256,payload.getList("evidence",Document.class).size());assertEquals(255,payload.get("observations",Document.class).size());assertEquals("PRIOR_WORK_ACCOUNTED",payload.getString("rule"));
        System.out.println("traversalJobs=255 generations="+attempts.size()+" totalMillis="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
    }
}
