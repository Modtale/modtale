package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationInventoryScaleTest {
    ProjectMutationObservationProgressTest fixture=new ProjectMutationObservationProgressTest();
    @AfterEach void cleanup(){fixture.cleanup();}
    @Test void readBatchAuthenticatesEachReceiptAndCannotEscapeItsScope()throws Exception{
        fixture.setup(2);var base=fixture.base;var candidate=base.candidate();var ids=new LinkedHashMap<String,String>();
        for(var work:base.base.prior.read(candidate.projectId(),candidate.mutationId(),()->true).work()){
            if(work.kind()!=ProjectMutationPriorWorkReader.Kind.REMOTE_JOB)continue;String id=UUID.randomUUID().toString();
            base.base.base.accounting.check(id,candidate.projectId(),work.mutationId(),work.versionId(),()->true);ids.put(work.versionId(),id);
        }
        ProjectMutationProgressTestSupport.assertReadBatchBoundaries(base.f().mongo,base.base.base.budget,base.base.base.history,base.f().client,base.scope(candidate),ids);
        assertEquals(2,fixture.reads.get());assertEquals(0,base.f().posts.get());
    }
    @ParameterizedTest @ValueSource(ints={16,255})
    void completeInventoryCanBeVerifiedAndActivatedWithinSharedWorkWindows(int jobs)throws Exception{
        fixture.setup(jobs);var base=fixture.base;var f=base.f();var candidate=base.candidate();
        var inventory=base.base.prior.read(candidate.projectId(),candidate.mutationId(),()->true);assertEquals(jobs+1,inventory.work().size());
        var ids=new LinkedHashMap<String,String>();long observationsStarted=System.nanoTime();
        for(var work:inventory.work()){
            if(work.kind()!=ProjectMutationPriorWorkReader.Kind.REMOTE_JOB)continue;
            String id=UUID.randomUUID().toString();var receipt=base.base.base.accounting.check(id,candidate.projectId(),work.mutationId(),work.versionId(),()->true);
            assertEquals("COMPLETED",receipt.observation().status().workState());ids.put(work.mutationId()+"/"+work.versionId(),id);
            ProjectMutationProgressTestSupport.retain(f.mongo,base.base.base.budget,base.base.base.accounting,base.scope(candidate),work,receipt);
        }
        assertEquals(jobs,fixture.reads.get());assertEquals(jobs,ids.size());
        var captured=new RawReviewSnapshotReader(f.mongo).capture(candidate.projectId(),candidate.versionIndex(),candidate.versionId());
        var archive=base.base.base.base.base.base.archive;var preparation=base.base.create(archive,Clock.systemUTC());
        long started=System.nanoTime();var prepared=preparation.prepare(new ProjectMutationAdmissionPreparation.Request(UUID.randomUUID().toString(),candidate.projectId(),candidate.versionIndex(),candidate.versionId(),captured.sha256(),candidate.mutationId(),"scale-reviewer",ids,false),()->true);
        long prepareMillis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
        var evidence=new RawBsonDocument(archive.load(prepared.id()).versionBytes()).decode(new DocumentCodec());assertEquals(jobs+1,evidence.getList("evidence",Document.class).size());assertEquals(jobs,evidence.get("observations",Document.class).size());
        var activator=new ProjectMutationActivator(f.mongo,base.base.base.budget,preparation,archive,new ReviewRepairJournal(f.mongo,archive));started=System.nanoTime();
        var automatic=new ProjectMutationAutomaticAdmission(f.mongo,base.base.base.budget,base.base.base.history,base.base.prior,base.attempts,base.base.base.accounting,preparation,activator,archive);
        var result=automatic.advance(candidate,()->true);assertEquals("ADMITTED",result.state());long activateMillis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
        var finalEvidence=new RawBsonDocument(archive.load(result.decisionId()).versionBytes()).decode(new DocumentCodec());assertEquals(jobs+1,finalEvidence.getList("evidence",Document.class).size());assertEquals(jobs,finalEvidence.get("observations",Document.class).size());
        assertEquals(jobs,fixture.reads.get());assertEquals(0,f.posts.get());
        System.out.println("inventoryJobs="+jobs+" preparationMillis="+prepareMillis+" automaticMillis="+activateMillis+" totalMillis="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-observationsStarted));
    }
}
