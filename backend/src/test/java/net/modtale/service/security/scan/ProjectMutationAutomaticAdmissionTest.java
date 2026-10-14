package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import net.modtale.model.project.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationAutomaticAdmissionTest {
    ProjectMutationAdmissionPreparationTest base=new ProjectMutationAdmissionPreparationTest();ProjectMutationAdmissionAttempts attempts;ProjectMutationActivator activator;ProjectMutationAutomaticAdmission automatic;
    @BeforeEach void setup()throws Exception{base.setup();attempts=new ProjectMutationAdmissionAttempts(f().mongo,base.base.budget,8,1000);
        activator=new ProjectMutationActivator(f().mongo,base.base.budget,base.service,base.base.base.base.base.archive,new ReviewRepairJournal(f().mongo,base.base.base.base.base.archive));automatic=create();}
    RemoteReviewStepIntegrationTest f(){return base.base.base.base.base.fixture;}
    ProjectMutationAutomaticAdmission create(){return new ProjectMutationAutomaticAdmission(f().mongo,base.base.budget,base.base.history,base.prior,attempts,base.base.accounting,base.service,activator,base.base.base.base.base.archive);}
    @AfterEach void cleanup(){base.cleanup();}
    ProjectMutationDiscovery.Candidate candidate(){return new ProjectMutationDiscovery(f().mongo).page(null,64).candidates().getFirst();}
    ProjectMutationAdmissionAttempts.Scope scope(ProjectMutationDiscovery.Candidate c){return new ProjectMutationAdmissionAttempts.Scope(c.projectId(),c.versionId(),c.mutationId(),c.requestId(),c.attempt());}
    @Test void completedOriginalIsAutomaticallyAdmittedWithoutClearance(){
        var c=candidate();var result=automatic.advance(c,()->true);assertEquals("ADMITTED",result.state());assertEquals("ADMITTED",attempts.status(scope(c),()->true).state());
        var retained=base.service.recover(result.decisionId(),ProjectMutationAutomaticAdmission.ACTOR,()->true);assertEquals("PRIOR_WORK_ACCOUNTED",retained.rule());assertEquals(c.requestId(),retained.binding().requestId());
        assertEquals("ADMITTED",create().advance(c,()->true).state());assertEquals(0,f().posts.get());assertEquals(ProjectVersion.ReviewStatus.PENDING,f().mongo.findById(f().project,Project.class).getVersions().get(1).getReviewStatus());
    }
    @Test void runningOriginalWaitsThenUsesANewObservationAfterCooldown()throws Exception {
        var c=candidate();base.state="RUNNING";base.workState="RUNNING";var first=automatic.advance(c,()->true);assertEquals("WAITING",first.state());assertEquals(1,f().gets.get());
        assertEquals(first,automatic.advance(c,()->true));assertEquals(1,f().gets.get());Thread.sleep(1100);base.state="COMPLETED";base.workState="COMPLETED";
        var next=create().advance(c,()->true);assertEquals("ADMITTED",next.state());assertNotEquals(first.decisionId(),next.decisionId());assertEquals(2,attempts.status(scope(c),()->true).attempts());
        assertEquals(2,f().mongo.getCollection(ProjectMutationJobAccounting.COLLECTION).countDocuments());assertEquals(0,f().posts.get());
    }
    @ParameterizedTest @ValueSource(ints={401,403,404,409})
    void unavailableIdentityRequiresAttentionWithoutAutomaticAcknowledgment(int status){base.status=status;var c=candidate();assertEquals("ATTENTION",automatic.advance(c,()->true).state());assertEquals("ATTENTION",automatic.advance(c,()->true).state());assertEquals(1,f().gets.get());assertTrue(new RemoteReviewDiscovery(f().mongo).page(null,64).candidates().isEmpty());}
    @ParameterizedTest @ValueSource(ints={429,503})
    void transientReadFailureCanWaitWithoutReplayingAnObservation(int status){base.status=status;var c=candidate();assertEquals("WAITING",automatic.advance(c,()->true).state());assertEquals(1,f().gets.get());assertEquals(0,f().posts.get());}
    @Test void completedOriginalWithRunningWorkerWaits(){base.workState="RUNNING";assertEquals("WAITING",automatic.advance(candidate(),()->true).state());}
    @Test void cancelledOriginalCannotBeAutomaticallyAcknowledged(){base.state="CANCELLED";base.workState=null;assertEquals("ATTENTION",automatic.advance(candidate(),()->true).state());assertEquals(0,f().posts.get());}
    @Test void runningAttemptIsNotReclaimedByAnotherCoordinator(){
        var c=candidate();var captured=new RawReviewSnapshotReader(f().mongo).capture(c.projectId(),c.versionIndex(),c.versionId());var claim=attempts.begin(scope(c),captured.sha256(),()->true);
        assertEquals("RUNNING",automatic.advance(c,()->true).state());assertEquals(claim,attempts.status(scope(c),()->true).current());assertEquals(0,f().gets.get());
    }
    @Test void committedAdmissionCanReconcileAnInterruptedAttempt(){reconcileCommitted(false);}
    @Test void committedProofDoesNotRewriteATerminalAttentionOutcome(){reconcileCommitted(true);}
    void reconcileCommitted(boolean attention){
        var c=candidate();var captured=new RawReviewSnapshotReader(f().mongo).capture(c.projectId(),c.versionIndex(),c.versionId());var claim=attempts.begin(scope(c),captured.sha256(),()->true);base.base.check();
        var prepared=base.service.prepare(new ProjectMutationAdmissionPreparation.Request(claim.decisionId(),c.projectId(),c.versionIndex(),c.versionId(),captured.sha256(),c.mutationId(),ProjectMutationAutomaticAdmission.ACTOR,Map.of(c.mutationId()+"/v",base.base.id),false),()->true);
        assertEquals("APPLIED",activator.activate(prepared,ProjectMutationAutomaticAdmission.ACTOR,()->true).state());if(attention)attempts.finish(claim,ProjectMutationAdmissionAttempts.Outcome.ATTENTION,()->true);
        assertEquals(attention?"ATTENTION":"RUNNING",attempts.status(scope(c),()->true).state());int before=f().gets.get();
        assertEquals("ADMITTED",create().advance(c,()->true).state());assertEquals(before,f().gets.get());assertEquals(attention?"ATTENTION":"ADMITTED",attempts.status(scope(c),()->true).state());
    }
    @Test void staleCandidateAndWithdrawnLifecycleCannotBegin(){
        var c=candidate();var stale=new ProjectMutationDiscovery.Candidate(c.projectId(),c.versionIndex(),c.versionId(),c.mutationId(),c.requestId(),c.attempt()+1);
        assertEquals("NO_WORK",automatic.advance(stale,()->true).state());f().mongo.getCollection("projects").updateOne(new Document("_id",c.projectId()),new Document("$set",new Document("status","DRAFT")));
        assertThrows(SecurityException.class,()->automatic.advance(c,()->true));assertEquals(0,f().mongo.getCollection(ProjectMutationAdmissionAttempts.COLLECTION).countDocuments());assertEquals(0,f().gets.get());
    }
    @Test void unresolvedPriorIdentityNeverReceivesAutomaticAcknowledgment(){
        base.unresolvedOriginalRequiresAcknowledgmentWithoutInventingAnObservation();f().gets.set(0);
        assertEquals("ATTENTION",automatic.advance(candidate(),()->true).state());assertEquals(0,f().gets.get());assertTrue(new RemoteReviewDiscovery(f().mongo).page(null,64).candidates().isEmpty());
    }
    @Test void uncertainActivationStopsAutomaticRetry(){
        var c=candidate();var journal=spy(new ReviewRepairJournal(f().mongo,base.base.base.base.base.archive));
        doAnswer(call->{var claim=call.callRealMethod();f().mongo.getCollection(ProjectMutationActivator.ADMISSIONS).insertOne(new Document("_id",c.requestId()).append("decisionId","collision"));return claim;}).when(journal).claim(any(),anyString(),any(),any());
        var racedActivator=new ProjectMutationActivator(f().mongo,base.base.budget,base.service,base.base.base.base.base.archive,journal);
        var raced=new ProjectMutationAutomaticAdmission(f().mongo,base.base.budget,base.base.history,base.prior,attempts,base.base.accounting,base.service,racedActivator,base.base.base.base.base.archive);
        assertEquals("ATTENTION",raced.advance(c,()->true).state());assertEquals("ATTENTION",attempts.status(scope(c),()->true).state());
        assertTrue(new RemoteReviewDiscovery(f().mongo).page(null,64).candidates().isEmpty());assertEquals(0,f().posts.get());
    }
    @Test void freshUploadDoesNotWaitForUnchangedOlderJobs(){
        base.purelyNewUploadNeedsNoObservationOfUnchangedOlderVersions();f().gets.set(0);var c=candidate();assertEquals("fresh",c.versionId());
        assertEquals("ADMITTED",automatic.advance(c,()->true).state());assertEquals(0,f().mongo.getCollection(ProjectMutationJobAccounting.COLLECTION).countDocuments());assertEquals(0,f().posts.get());
    }
}
