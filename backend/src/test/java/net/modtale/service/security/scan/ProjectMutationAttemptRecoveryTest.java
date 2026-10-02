package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationAttemptRecoveryTest {
    ProjectMutationAutomaticAdmissionTest base=new ProjectMutationAutomaticAdmissionTest();
    ProjectMutationAttemptRecovery recovery;
    @BeforeEach void setup()throws Exception{base.setup();recovery=new ProjectMutationAttemptRecovery(base.f().mongo,base.base.base.budget,base.attempts,base.base.base.base.base.base.archive);recovery.initialize();}
    @AfterEach void cleanup(){base.cleanup();}
    ProjectMutationAdmissionAttempts.Claim claim(){var c=base.candidate();var capture=new RawReviewSnapshotReader(base.f().mongo).capture(c.projectId(),c.versionIndex(),c.versionId());return base.attempts.begin(base.scope(c),capture.sha256(),()->true);}
    ProjectMutationAdmissionAttempts.Claim committed(){
        var c=base.candidate();var claim=claim();base.base.base.check();
        var decision=base.base.service.prepare(new ProjectMutationAdmissionPreparation.Request(claim.decisionId(),c.projectId(),c.versionIndex(),c.versionId(),claim.heldSha256(),c.mutationId(),ProjectMutationAutomaticAdmission.ACTOR,Map.of(c.mutationId()+"/v",base.base.base.id),false),()->true);
        assertEquals("APPLIED",base.activator.activate(decision,ProjectMutationAutomaticAdmission.ACTOR,()->true).state());return claim;
    }
    @Test void backgroundRecoveryFindsCommittedAdmissionAfterProjectDeletion()throws Exception{
        var claim=committed();int gets=base.f().gets.get();base.f().mongo.getCollection("projects").deleteMany(new Document());
        assertEquals(List.of(claim.scope()),recovery.page(null,4).candidates());
        try(var scheduler=new ProjectMutationRecoveryScheduler(recovery,new ProjectMutationRecoveryScheduler.Settings(1,4,100,1000))){
            scheduler.start();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(scheduler.status().processed()==0&&System.nanoTime()<deadline)Thread.sleep(20);
            assertEquals("ADMITTED",scheduler.status().lastOutcome());
        }
        assertEquals("ADMITTED",base.attempts.status(claim.scope(),()->true).state());assertTrue(recovery.page(null,4).candidates().isEmpty());assertEquals(gets,base.f().gets.get());assertEquals(0,base.f().posts.get());
    }
    @Test void unresolvedExecutionIsNeverReclaimed(){var claim=claim();assertEquals("UNRESOLVED",recovery.recover(claim.scope(),()->true));assertEquals(claim,base.attempts.status(claim.scope(),()->true).current());assertEquals("RUNNING",base.attempts.status(claim.scope(),()->true).state());assertEquals(0,base.f().gets.get());}
    @Test void terminalAttentionIsNotRewritten(){var claim=committed();base.attempts.finish(claim,ProjectMutationAdmissionAttempts.Outcome.ATTENTION,()->true);assertEquals("ATTENTION",recovery.recover(claim.scope(),()->true));assertTrue(recovery.page(null,4).candidates().isEmpty());}
    @Test void changedReceiptCannotFinishAnAttempt(){var claim=committed();base.f().mongo.getCollection(ProjectMutationActivator.ADMISSIONS).updateOne(new Document("_id",claim.scope().requestId()),new Document("$set",new Document("afterSha256","0".repeat(64))));assertEquals("UNRESOLVED",recovery.recover(claim.scope(),()->true));assertEquals("RUNNING",base.attempts.status(claim.scope(),()->true).state());}
    @Test void validReceiptForDifferentHeldBytesCannotFinishAttempt(){var claim=committed();base.f().mongo.getCollection(ProjectMutationAdmissionAttempts.COLLECTION).updateOne(new Document("_id",claim.scope().requestId()),new Document("$set",new Document("attempts.0.heldSha256","0".repeat(64))));assertEquals("UNRESOLVED",recovery.recover(claim.scope(),()->true));assertEquals("RUNNING",base.attempts.status(claim.scope(),()->true).state());}
    @Test void permissionRevocationLeavesAttemptRunning(){var claim=committed();assertThrows(SecurityException.class,()->recovery.recover(claim.scope(),()->false));assertEquals("RUNNING",base.attempts.status(claim.scope(),()->true).state());}
    @Test void malformedScopeDoesNotPreventPageContinuation(){
        var claim=claim();var records=base.f().mongo.getCollection(ProjectMutationAdmissionAttempts.COLLECTION);
        records.insertOne(new Document("_id","00000000-0000-0000-0000-000000000000").append("state","RUNNING").append("scope",new Document("projectId",List.of("bad"))).append("attempts","x".repeat(1000000)));
        var first=recovery.page(null,1);assertEquals(1,first.examined());assertEquals(1,first.unavailable());assertTrue(first.candidates().isEmpty());assertNotNull(first.next());
        var second=recovery.page(first.next(),1);assertEquals(List.of(claim.scope()),second.candidates());assertNull(second.next());
    }
    @Test void traversalRequiresIndexAndValidBounds(){assertThrows(IllegalStateException.class,()->recovery.page(null,0));assertThrows(IllegalStateException.class,()->recovery.page(null,65));assertThrows(IllegalStateException.class,()->recovery.page("bad",1));base.f().mongo.getCollection(ProjectMutationAdmissionAttempts.COLLECTION).dropIndex(ProjectMutationAttemptRecovery.INDEX);assertThrows(com.mongodb.MongoException.class,()->recovery.page(null,1));}
}
