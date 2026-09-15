package net.modtale.service.security.scan;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationAdmissionAttemptsTest {
    ProjectMutationJobAccountingTest base=new ProjectMutationJobAccountingTest();ProjectMutationAdmissionAttempts attempts;ProjectMutationAdmissionAttempts.Scope scope;
    @BeforeEach void setup()throws Exception{base.setup();var v=base.base.root().getList("versions",Document.class).get(1);var scan=v.get("scanResult",Document.class);
        scope=new ProjectMutationAdmissionAttempts.Scope(base.project,"new-upload",base.prepared.id(),scan.getString("scanRequestId"),scan.getInteger("scanAttempt"));attempts=create(mongo(),base.budget);}
    MongoTemplate mongo(){return base.base.base.base.fixture.mongo;}
    ProjectMutationAdmissionAttempts create(MongoTemplate mongo,ReviewRepairWorkflow budget){return new ProjectMutationAdmissionAttempts(mongo,budget,2,200);}
    @AfterEach void cleanup(){base.cleanup();}
    ProjectMutationAdmissionAttempts.Claim begin(){return attempts.begin(scope,"a".repeat(64),()->true);}
    @Test void runningAttemptSurvivesRestartWithoutBeingReclaimed(){
        var claim=begin();assertNotNull(claim);var restarted=create(mongo(),base.budget);assertEquals(claim,restarted.status(scope,()->true).current());assertNull(restarted.begin(scope,"b".repeat(64),()->true));
        mongo().getCollection(ProjectMutationAdmissionAttempts.COLLECTION).updateOne(new Document("_id",scope.requestId()),new Document("$set",new Document("attempts.0.startedAt",new Date(1))));
        assertNull(begin());assertEquals("RUNNING",attempts.status(scope,()->true).state());
    }
    @Test void completedWaitingAttemptGetsNewIdentityOnlyAfterCooldown()throws Exception {
        var first=begin();var waiting=attempts.finish(first,ProjectMutationAdmissionAttempts.Outcome.WAITING,()->true);assertEquals("WAITING",waiting.state());assertNull(begin());
        Thread.sleep(250);var second=attempts.begin(scope,"b".repeat(64),()->true);assertNotNull(second);assertEquals(2,second.sequence());assertNotEquals(first.decisionId(),second.decisionId());
        assertThrows(IllegalStateException.class,()->attempts.finish(first,ProjectMutationAdmissionAttempts.Outcome.ADMITTED,()->true));
        var limit=attempts.finish(second,ProjectMutationAdmissionAttempts.Outcome.WAITING,()->true);assertEquals("ATTENTION",limit.state());Thread.sleep(250);assertNull(begin());
        assertEquals(2,mongo().getCollection(ProjectMutationAdmissionAttempts.COLLECTION).find().first().getList("attempts",Document.class).size());
    }
    @Test void finishedAttemptIsIdempotentButCannotChangeOutcome(){
        var claim=begin();var done=attempts.finish(claim,ProjectMutationAdmissionAttempts.Outcome.ADMITTED,()->true);
        assertEquals(done,attempts.finish(claim,ProjectMutationAdmissionAttempts.Outcome.ADMITTED,()->true));assertNull(begin());
        assertThrows(IllegalStateException.class,()->attempts.finish(claim,ProjectMutationAdmissionAttempts.Outcome.WAITING,()->true));
    }
    @Test void scopeCannotBeRetargetedEvenToLookalikeProjectId(){
        begin();var other=new ProjectMutationAdmissionAttempts.Scope(scope.projectId().toString(),scope.versionId(),scope.mutationId(),scope.requestId(),scope.scanAttempt());
        assertThrows(IllegalStateException.class,()->attempts.begin(other,"a".repeat(64),()->true));assertThrows(SecurityException.class,()->attempts.status(scope,()->false));
    }
    @Test void independentWorkersCannotClaimTheSameGeneration()throws Exception {
        try(var budget=new ReviewRepairWorkflow(mock(ReviewRepairPreparation.class),mock(ReviewIsolationExecutor.class),1);var workers=Executors.newFixedThreadPool(2)) {
            var second=create(mongo(),budget);var start=new CountDownLatch(1);
            var a=workers.submit(()->{start.await();return begin();});var b=workers.submit(()->{start.await();return second.begin(scope,"b".repeat(64),()->true);});start.countDown();
            var claims=Arrays.asList(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));assertEquals(1,claims.stream().filter(Objects::nonNull).count());assertEquals(1,attempts.status(scope,()->true).attempts());
        }
    }
    @Test void alteredRetainedAttemptIsNotReinterpreted(){
        begin();mongo().getCollection(ProjectMutationAdmissionAttempts.COLLECTION).updateOne(new Document("_id",scope.requestId()),new Document("$set",new Document("attempts.0.decisionId",UUID.randomUUID().toString())));
        assertThrows(IllegalStateException.class,()->attempts.status(scope,()->true));assertThrows(IllegalStateException.class,this::begin);
    }
    MongoTemplate wrapped(MongoCollection<Document> collection){
        doReturn(collection).when(collection).withReadPreference(any());doReturn(collection).when(collection).withReadConcern(any());doReturn(collection).when(collection).withWriteConcern(any());doReturn(collection).when(collection).withTimeout(anyLong(),any());
        var mongo=spy(mongo());doReturn(collection).when(mongo).getCollection(ProjectMutationAdmissionAttempts.COLLECTION);return mongo;
    }
    @Test void lostClaimReplyReturnsOnlyTheRetainedClaim(){
        var collection=spy(mongo().getCollection(ProjectMutationAdmissionAttempts.COLLECTION));var wrapped=wrapped(collection);
        doAnswer(call->{call.callRealMethod();throw new MongoException("lost claim reply");}).when(collection).findOneAndUpdate(any(Bson.class),anyList(),any(FindOneAndUpdateOptions.class));
        var claim=create(wrapped,base.budget).begin(scope,"a".repeat(64),()->true);assertNotNull(claim);assertEquals(claim,attempts.status(scope,()->true).current());assertNull(begin());
    }
    @Test void lostFinishReplyDoesNotCreateAnotherAttempt(){
        var claim=begin();var collection=spy(mongo().getCollection(ProjectMutationAdmissionAttempts.COLLECTION));var wrapped=wrapped(collection);
        doAnswer(call->{call.callRealMethod();throw new MongoException("lost finish reply");}).when(collection).updateOne(any(Bson.class),anyList(),any(UpdateOptions.class));
        var result=create(wrapped,base.budget).finish(claim,ProjectMutationAdmissionAttempts.Outcome.ATTENTION,()->true);assertEquals("ATTENTION",result.state());assertEquals(1,result.attempts());assertNull(begin());
    }
}
