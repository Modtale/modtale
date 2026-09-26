package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationCombinedHistoryTest {
    ProjectMutationObservationProgressTest fixture=new ProjectMutationObservationProgressTest();
    @AfterEach void cleanup(){fixture.cleanup();}
    @Test void maximumWorkInventoryWithLargeEightGroupHistoryCanCompleteAdmission()throws Exception{
        fixture.setup(255);var base=fixture.base;var f=base.f();var archive=base.base.base.base.base.base.archive;
        var historyFixture=new ProjectMutationHistoryBudgetTest();historyFixture.base=base;long sourceBytes=historyFixture.stage(2750000,8);
        assertTrue(sourceBytes>60L*1024*1024 && sourceBytes<=64L*1024*1024,"sourceBytes="+sourceBytes);
        var candidate=base.candidate();var inventory=base.base.prior.read(candidate.projectId(),candidate.mutationId(),()->true);
        assertEquals(8,inventory.groups().size());assertEquals(256,inventory.work().size());
        for(var work:inventory.work()){
            if(work.kind()!=ProjectMutationPriorWorkReader.Kind.REMOTE_JOB)continue;
            var receipt=base.base.base.accounting.check(UUID.randomUUID().toString(),candidate.projectId(),work.mutationId(),work.versionId(),()->true);
            assertEquals("COMPLETED",receipt.observation().status().workState());
            ProjectMutationProgressTestSupport.retain(f.mongo,base.base.base.budget,base.base.base.accounting,base.scope(candidate),work,receipt);
        }
        assertEquals(255,fixture.reads.get());
        var preparation=base.base.create(archive,Clock.systemUTC());var activator=new ProjectMutationActivator(f.mongo,base.base.base.budget,preparation,archive,new ReviewRepairJournal(f.mongo,archive));
        var automatic=new ProjectMutationAutomaticAdmission(f.mongo,base.base.base.budget,base.base.base.history,base.base.prior,base.attempts,base.base.base.accounting,preparation,activator,archive);
        long started=System.nanoTime();var result=automatic.advance(candidate,()->true);long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);assertEquals("ADMITTED",result.state());
        var evidence=new RawBsonDocument(archive.load(result.decisionId()).versionBytes()).decode(new DocumentCodec());
        assertEquals(8,evidence.getList("groups",String.class).size());assertEquals(256,evidence.getList("evidence",Document.class).size());assertEquals(255,evidence.get("observations",Document.class).size());
        assertEquals("PRIOR_WORK_ACCOUNTED",evidence.getString("rule"));assertEquals(255,fixture.reads.get());assertEquals(0,f.posts.get());
        System.out.println("combinedGroups=8 jobs=255 sourceBytes="+sourceBytes+" automaticMillis="+elapsed);
    }
}
