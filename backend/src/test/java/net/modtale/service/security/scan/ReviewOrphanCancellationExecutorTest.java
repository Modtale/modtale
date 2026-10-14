package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import com.mongodb.MongoException;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewOrphanCancellationExecutorTest {
    ReviewOrphanCancellationJournalTest base=new ReviewOrphanCancellationJournalTest();
    RemoteReviewStepIntegrationTest f;ReviewOrphanCancellationJournal.Prepared prepared;ReviewOrphanCancellationExecutor executor;
    AtomicInteger deletes=new AtomicInteger();
    @BeforeEach void setup()throws Exception {base.setup();f=base.fixture.fixture;prepared=base.prepare();executor=create(base.journal,Duration.ofSeconds(5));}
    @AfterEach void cleanup(){executor.close();base.cleanup();}
    ReviewOrphanCancellationExecutor create(ReviewOrphanCancellationJournal journal,Duration budget){return new ReviewOrphanCancellationExecutor(journal,f.client,1,budget);}
    ReviewOrphanCancellationExecutor.Execution execute(){return executor.execute(prepared,"actor",()->true);}
    void route(int code,String state){f.route(e->{assertEquals("DELETE",e.getRequestMethod());deletes.incrementAndGet();f.reply(e,code,state);});}
    @ParameterizedTest @ValueSource(strings={"CANCELLED","COMPLETED","HELD","EXPIRED","QUEUED","RUNNING"})
    void recordsActualRemoteStateWithoutGrantingClearanceOrRepeatingDispatch(String state) {
        route(200,state);var before=base.fixture.raw();var result=execute();assertEquals("OBSERVED",result.state());assertEquals("REMOTE_STATUS",result.receipt().observation().kind());
        assertEquals(state,result.receipt().observation().status().state());assertNotNull(result.receipt().observedAt());
        assertEquals(result,create(base.journal,Duration.ofSeconds(5)).execute(prepared,"actor",()->true));assertEquals(1,deletes.get());assertEquals(0,f.posts.get());assertEquals(0,f.identityGets.get());
        assertEquals(before,base.fixture.raw());base.fixture.assertIsolated();assertEquals("BLOCK",f.saved().getVerdict());verifyNoInteractions(f.storage);
    }
    @ParameterizedTest @ValueSource(ints={404,409,401,403,429,503})
    void rejectedAndUncertainResponsesAreDistinctAndCannotRetry(int code) {
        route(code,"QUEUED");var result=execute();String kind=code==404?"NOT_FOUND":code==409?"CONTEXT_CONFLICT":code==503?"UNKNOWN":"UNAVAILABLE";
        assertEquals(kind,result.receipt().observation().kind());assertEquals(code,result.receipt().observation().httpStatus());assertNull(result.receipt().observation().status());
        assertEquals(result,execute());assertEquals(1,deletes.get());
    }
    @Test void mismatchedRemoteJobIsUnknownAndNeverCleared() {
        f.route(e->{deletes.incrementAndGet();f.job=UUID.randomUUID().toString();f.reply(e,200,"CANCELLED");});
        var result=execute();assertEquals("UNKNOWN",result.state());assertNull(result.receipt().observation().status());assertEquals(502,result.receipt().observation().httpStatus());assertEquals(result,execute());assertEquals(1,deletes.get());
    }
    @Test void droppedResponseHasOneHttpAttemptAndDurableUnknownOutcome() {
        f.route(e->{deletes.incrementAndGet();e.close();});var result=execute();assertEquals("UNKNOWN",result.state());assertEquals(result,execute());assertEquals(1,deletes.get());
    }
    @Test void remainingBudgetBoundsHttpWaitAndLeavesNoRetryAuthority()throws Exception {
        var arrived=new CountDownLatch(1);var release=new CountDownLatch(1);var done=new CountDownLatch(1);
        f.route(e->{deletes.incrementAndGet();arrived.countDown();try{release.await(5,TimeUnit.SECONDS);f.reply(e,200,"CANCELLED");}finally{done.countDown();}});
        try(var bounded=create(base.journal,Duration.ofSeconds(1));var pool=Executors.newSingleThreadExecutor()) {
            var result=pool.submit(()->bounded.execute(prepared,"actor",()->true));assertTrue(arrived.await(3,TimeUnit.SECONDS));
            var observed=result.get(4,TimeUnit.SECONDS);assertEquals("UNKNOWN",observed.state());assertEquals(observed,execute());assertEquals(1,deletes.get());
        }finally{release.countDown();assertTrue(done.await(3,TimeUnit.SECONDS));}
    }
    @Test void localAdmissionAndAnotherReplicaCannotDuplicateAnInFlightDispatch()throws Exception {
        var arrived=new CountDownLatch(1);var release=new CountDownLatch(1);
        f.route(e->{deletes.incrementAndGet();arrived.countDown();release.await(5,TimeUnit.SECONDS);f.reply(e,200,"CANCELLED");});
        try(var pool=Executors.newSingleThreadExecutor();var replica=create(base.journal,Duration.ofSeconds(5))) {
            var first=pool.submit(this::execute);assertTrue(arrived.await(3,TimeUnit.SECONDS));assertEquals("BUSY",execute().state());
            assertEquals("DISPATCHING",replica.execute(prepared,"actor",()->true).state());release.countDown();assertEquals("OBSERVED",first.get(4,TimeUnit.SECONDS).state());assertEquals(1,deletes.get());
        }finally{release.countDown();}
    }
    @Test void lostDispatchAcknowledgementNeverRecoversIntoAnHttpRequest() {
        var journal=spy(base.journal);doAnswer(i->{i.callRealMethod();throw new MongoException("Unknown dispatch acknowledgement");}).when(journal).beginDispatch(any(),anyString(),any());
        route(200,"CANCELLED");var result=create(journal,Duration.ofSeconds(5)).execute(prepared,"actor",()->true);
        assertEquals("UNKNOWN",result.state());assertEquals(0,deletes.get());assertEquals(result,execute());
    }
    @Test void lostObservationAcknowledgementReadsBackTheCommittedReceipt() {
        var mongo=spy(f.mongo);var ops=base.intercepted(mongo);
        doAnswer(i->{i.callRealMethod();throw new MongoException("Lost outcome acknowledgement");}).when(ops).updateOne(any(org.bson.conversions.Bson.class),anyList(),any(com.mongodb.client.model.UpdateOptions.class));
        route(200,"CANCELLED");var result=create(base.create(mongo,Clock.systemUTC()),Duration.ofSeconds(5)).execute(prepared,"actor",()->true);
        assertEquals("OBSERVED",result.state());assertEquals(result,execute());assertEquals(1,deletes.get());
    }
    @Test void aTerminalObservationCannotBeOverwrittenByLaterBookkeeping() {
        route(200,"COMPLETED");var first=execute();var record=base.operations().find().first();
        var claim=new ReviewOrphanCancellationJournal.Claim(prepared,record.getString("token"),base.resolver.resolve(prepared.isolationId(),"actor"));
        var later=new ReviewOrphanCancellationJournal.Observation("REMOTE_STATUS",new RemoteReviewClient.Status(f.job,"CANCELLED",true,1000,2000,null),null);
        assertEquals(first.receipt(),base.journal.record(claim,"actor",later,()->true));assertEquals(record,base.operations().find().first());
    }
    @Test void revokedPermissionAfterRemoteReplyCannotPublishAnUnauthorizedReceiptOrRetry() {
        var allowed=new AtomicBoolean(true);f.route(e->{deletes.incrementAndGet();allowed.set(false);f.reply(e,200,"CANCELLED");});
        assertThrows(SecurityException.class,()->executor.execute(prepared,"actor",allowed::get));assertEquals("DISPATCHING",execute().state());assertEquals(1,deletes.get());
    }
    @Test void shutdownRefusesNewClaimsAndPermissionIsCheckedFirst() {
        executor.close();assertEquals("SHUTDOWN",execute().state());assertEquals("PREPARED",base.operations().find().first().getString("state"));
        assertThrows(SecurityException.class,()->executor.execute(prepared,"actor",()->false));assertEquals(0,deletes.get());
    }
    @Test void forgedObservationForAnotherJobCannotBeRetained() {
        var claim=base.journal.claim(prepared,"actor",()->true);assertTrue(base.journal.beginDispatch(claim,"actor",()->true)>0);
        var observation=new ReviewOrphanCancellationJournal.Observation("REMOTE_STATUS",new RemoteReviewClient.Status(UUID.randomUUID().toString(),"CANCELLED",true,1000,2000,null),null);
        assertThrows(IllegalStateException.class,()->base.journal.record(claim,"actor",observation,()->true));assertEquals("DISPATCHING",base.operations().find().first().getString("state"));
    }
    @Test void theSameClaimCannotCrossTheDispatchBoundaryTwice() {
        var claim=base.journal.claim(prepared,"actor",()->true);assertTrue(base.journal.beginDispatch(claim,"actor",()->true)>0);
        assertEquals(0,base.journal.beginDispatch(claim,"actor",()->true));assertEquals(0,deletes.get());
        var forged=new ReviewOrphanCancellationJournal.Claim(prepared,UUID.randomUUID().toString(),claim.target());
        assertThrows(IllegalStateException.class,()->base.journal.beginDispatch(forged,"actor",()->true));
    }
    @Test void revocationImmediatelyAfterDispatchGatePreventsHttpSubscription() {
        var allowed=new AtomicBoolean(true);var journal=spy(base.journal);
        doAnswer(i->{var result=i.callRealMethod();allowed.set(false);return result;}).when(journal).beginDispatch(any(),anyString(),any());
        route(200,"CANCELLED");assertThrows(SecurityException.class,()->create(journal,Duration.ofSeconds(5)).execute(prepared,"actor",allowed::get));
        assertEquals("DISPATCHING",base.operations().find().first().getString("state"));assertEquals(0,deletes.get());assertEquals("DISPATCHING",execute().state());
    }

    @Test void deadlineBeforeArmingIsOperationalStateRatherThanPermissionDenial() {
        var journal=spy(base.journal);doAnswer(i->{Thread.sleep(1100);return i.callRealMethod();}).when(journal).claim(any(),anyString(),any());
        route(200,"CANCELLED");var result=create(journal,Duration.ofSeconds(1)).execute(prepared,"actor",()->true);
        assertEquals("PREPARED",result.state());assertEquals(0,deletes.get());assertFalse(base.operations().find().first().containsKey("token"));
    }

}
