package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import com.mongodb.*;
import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewCancellationReconcilerTest {
    ReviewOrphanCancellationJournalTest base=new ReviewOrphanCancellationJournalTest();RemoteReviewStepIntegrationTest f;
    ReviewOrphanCancellationJournal.Prepared original;ReviewCancellationReconciler reconciler;String id=UUID.randomUUID().toString();AtomicInteger reads=new AtomicInteger();
    @BeforeEach void setup()throws Exception {
        base.setup();f=base.fixture.fixture;original=base.prepare();var claim=base.journal.claim(original,"actor",()->true);assertTrue(base.journal.beginDispatch(claim,"actor",()->true)>0);
        assertEquals("UNKNOWN",base.journal.record(claim,"actor",new ReviewOrphanCancellationJournal.Observation("UNKNOWN",null,503),()->true).state());
        reconciler=create(f.mongo,Duration.ofSeconds(5));
    }
    @AfterEach void cleanup(){reconciler.close();base.cleanup();}
    ReviewCancellationReconciler create(MongoTemplate mongo,Duration budget){return new ReviewCancellationReconciler(mongo,base.journal,f.client,1,budget);}
    MongoCollection<Document> observations(){return f.mongo.getCollection(ReviewCancellationReconciler.COLLECTION);}
    ReviewCancellationReconciler.Receipt check(){return reconciler.check(id,original,"actor",()->true);}
    void route(int code,String state){f.route(e->{assertEquals("GET",e.getRequestMethod());reads.incrementAndGet();f.reply(e,code,state);});}
    @Test void aLaterStatusPreservesTheOriginalUnknownOutcomeAndSurvivesRecreation() {
        route(200,"CANCELLED");var before=base.operations().find().first();var project=base.fixture.raw();var result=check();
        assertEquals("OBSERVED",result.state());assertEquals("CANCELLED",result.observation().status().state());assertNotNull(result.receivedAt());
        assertEquals(result,create(f.mongo,Duration.ofSeconds(5)).check(id,original,"actor",()->true));assertEquals(result,reconciler.receipt(id,original,"actor",()->true));
        assertEquals(1,reads.get());assertEquals(0,f.posts.get());assertEquals(0,f.identityGets.get());assertEquals(before,base.operations().find().first());assertEquals(project,base.fixture.raw());verifyNoInteractions(f.storage);
        assertNull(base.journal.claim(original,"actor",()->true));
    }
    @Test void currentVersionEditsOrDeletionCannotRetargetTheOriginalStatusRequest() {
        f.change("scanResult.remoteReview.jobId",UUID.randomUUID().toString());f.mongo.getCollection("projects").deleteMany(new Document());
        route(200,"COMPLETED");assertEquals(f.job,check().observation().status().jobId());assertEquals(1,reads.get());
    }
    @Test void distinctExplicitChecksKeepBothObservationsWithoutOverwritingHistory() {
        f.route(e->{assertEquals("GET",e.getRequestMethod());f.reply(e,200,reads.incrementAndGet()==1?"QUEUED":"CANCELLED");});
        var first=check();var second=reconciler.check(UUID.randomUUID().toString(),original,"actor",()->true);
        assertEquals("QUEUED",first.observation().status().state());assertEquals("CANCELLED",second.observation().status().state());assertEquals(first,reconciler.receipt(id,original,"actor",()->true));
        assertEquals(2,observations().countDocuments());assertEquals("UNKNOWN",base.operations().find().first().getString("state"));
    }
    @ParameterizedTest @ValueSource(ints={404,409,401,403,429,503})
    void statusFailuresAreRetainedWithoutCancellingOrRepeatingTheRead(int code) {
        route(code,"QUEUED");var result=check();String kind=code==404?"NOT_FOUND":code==409?"CONTEXT_CONFLICT":code==503?"UNKNOWN":"UNAVAILABLE";
        assertEquals(kind,result.observation().kind());assertEquals(code,result.observation().httpStatus());assertEquals(result,check());assertEquals(1,reads.get());
        assertEquals("UNKNOWN",base.operations().find().first().getString("state"));
    }
    @Test void permissionAndOriginalIntentValidationPrecedeReservation() {
        assertThrows(SecurityException.class,()->reconciler.check(id,original,"actor",()->false));assertThrows(SecurityException.class,()->reconciler.check(id,original,"other",()->true));
        var forged=new ReviewOrphanCancellationJournal.Prepared(original.isolationId(),"f".repeat(64),original.createdAt(),original.expiresAt());
        assertThrows(IllegalStateException.class,()->reconciler.check(id,forged,"actor",()->true));assertEquals(0,observations().countDocuments());assertEquals(0,reads.get());
    }
    @Test void unstartedCancellationCannotCreateAnObservation() {
        var record=base.operations().find().first();base.operations().replaceOne(new Document("_id",original.isolationId()),new Document("_id",original.isolationId()).append("intent",record.get("intent")).append("state","PREPARED"));
        assertThrows(IllegalStateException.class,this::check);assertEquals(0,observations().countDocuments());
    }
    @Test void competingChecksAndLocalAdmissionDoNotDuplicateARead()throws Exception {
        var arrived=new CountDownLatch(1);var release=new CountDownLatch(1);f.route(e->{assertEquals("GET",e.getRequestMethod());reads.incrementAndGet();arrived.countDown();release.await(5,TimeUnit.SECONDS);f.reply(e,200,"CANCELLED");});
        try(var pool=Executors.newSingleThreadExecutor();var replica=create(f.mongo,Duration.ofSeconds(5))) {
            var first=pool.submit(this::check);assertTrue(arrived.await(3,TimeUnit.SECONDS));assertThrows(IllegalStateException.class,()->reconciler.check(UUID.randomUUID().toString(),original,"actor",()->true));
            assertEquals("READING",replica.check(id,original,"actor",()->true).state());release.countDown();assertEquals("OBSERVED",first.get(4,TimeUnit.SECONDS).state());assertEquals(1,reads.get());
        }finally{release.countDown();}
    }
    MongoCollection<Document> intercepted(MongoTemplate template) {
        var ops=spy(observations().withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY).withWriteConcern(WriteConcern.MAJORITY.withJournal(true)));
        doReturn(ops).when(template).getCollection(ReviewCancellationReconciler.COLLECTION);doReturn(ops).when(ops).withReadPreference(any());doReturn(ops).when(ops).withReadConcern(any());doReturn(ops).when(ops).withWriteConcern(any());doReturn(ops).when(ops).withTimeout(anyLong(),any());return ops;
    }
    @Test void lostReservationAcknowledgementRecoversOnlyTheSameReadToken() {
        var mongo=spy(f.mongo);var ops=intercepted(mongo);doAnswer(i->{i.callRealMethod();throw new MongoException("Lost reservation acknowledgement");}).when(ops).insertOne(any(Document.class));
        route(200,"CANCELLED");var result=create(mongo,Duration.ofSeconds(5)).check(id,original,"actor",()->true);assertEquals("OBSERVED",result.state());assertEquals(result,check());assertEquals(1,reads.get());
    }
    @Test void lostReadGateAcknowledgementCannotStartAnHttpRequest() {
        var mongo=spy(f.mongo);var ops=intercepted(mongo);doAnswer(i->{i.callRealMethod();throw new MongoException("Lost read gate acknowledgement");}).when(ops).findOneAndUpdate(any(org.bson.conversions.Bson.class),anyList(),any(com.mongodb.client.model.FindOneAndUpdateOptions.class));
        route(200,"CANCELLED");var result=create(mongo,Duration.ofSeconds(5)).check(id,original,"actor",()->true);assertEquals("UNKNOWN",result.state());assertEquals(result,check());assertEquals(0,reads.get());
    }
    @Test void lostObservationAcknowledgementReadsBackWithoutAnotherStatusCall() {
        var mongo=spy(f.mongo);var ops=intercepted(mongo);doAnswer(i->{i.callRealMethod();throw new MongoException("Lost observation acknowledgement");}).when(ops).updateOne(any(org.bson.conversions.Bson.class),anyList(),any(com.mongodb.client.model.UpdateOptions.class));
        route(200,"CANCELLED");var result=create(mongo,Duration.ofSeconds(5)).check(id,original,"actor",()->true);assertEquals("OBSERVED",result.state());assertEquals(result,check());assertEquals(1,reads.get());
    }
    @Test void droppedReplyProducesOneReadAndUnknownObservation() {
        f.route(e->{assertEquals("GET",e.getRequestMethod());reads.incrementAndGet();e.close();});var result=check();assertEquals("UNKNOWN",result.state());assertEquals(result,check());assertEquals(1,reads.get());
    }
    @Test void tamperedObservationCannotBeReadOrOverwrittenAsAValidResult() {
        route(200,"CANCELLED");check();observations().updateOne(new Document("_id",id),new Document("$set",new Document("observation.status.jobId",UUID.randomUUID().toString())));
        var stored=observations().find().first();assertThrows(IllegalStateException.class,()->reconciler.receipt(id,original,"actor",()->true));assertThrows(IllegalStateException.class,this::check);
        assertEquals(stored,observations().find().first());assertEquals(1,reads.get());
    }
    @Test void permissionRevocationAfterReplyLeavesAnUnresolvedReadWithoutReplay() {
        var allowed=new AtomicBoolean(true);f.route(e->{assertEquals("GET",e.getRequestMethod());reads.incrementAndGet();allowed.set(false);f.reply(e,200,"CANCELLED");});
        assertThrows(SecurityException.class,()->reconciler.check(id,original,"actor",allowed::get));assertEquals("READING",check().state());assertEquals(1,reads.get());
    }
    @Test void shutdownAndInvalidIdsDoNotAllocateObservationRecords() {
        assertThrows(IllegalArgumentException.class,()->reconciler.check("bad",original,"actor",()->true));reconciler.close();assertThrows(IllegalStateException.class,this::check);
        assertThrows(SecurityException.class,()->reconciler.check(id,original,"actor",()->false));assertEquals(0,observations().countDocuments());
    }
    @Test void remainingReadBudgetBoundsWaitingWithoutRepeatingTheGet()throws Exception {
        var arrived=new CountDownLatch(1);var release=new CountDownLatch(1);var done=new CountDownLatch(1);
        f.route(e->{assertEquals("GET",e.getRequestMethod());reads.incrementAndGet();arrived.countDown();try{release.await(5,TimeUnit.SECONDS);f.reply(e,200,"CANCELLED");}finally{done.countDown();}});
        try(var bounded=create(f.mongo,Duration.ofSeconds(1));var pool=Executors.newSingleThreadExecutor()) {
            var request=pool.submit(()->bounded.check(id,original,"actor",()->true));assertTrue(arrived.await(3,TimeUnit.SECONDS));
            var result=request.get(4,TimeUnit.SECONDS);assertEquals("UNKNOWN",result.state());assertEquals(result,check());assertEquals(1,reads.get());
        }finally{release.countDown();assertTrue(done.await(3,TimeUnit.SECONDS));}
    }
    @Test void wrongRemoteJobCannotBecomeAValidReconciliationObservation() {
        f.route(e->{assertEquals("GET",e.getRequestMethod());reads.incrementAndGet();f.job=UUID.randomUUID().toString();f.reply(e,200,"CANCELLED");});
        var result=check();assertEquals("UNKNOWN",result.state());assertEquals(502,result.observation().httpStatus());assertNull(result.observation().status());assertEquals(result,check());assertEquals(1,reads.get());
    }
    @Test void sourceTamperingAfterReservationCannotReachTheRemoteService() {
        var journal=spy(base.journal);var count=new AtomicInteger();
        doAnswer(i->{var target=i.callRealMethod();if(count.incrementAndGet()==1)f.mongo.getCollection(ReviewSnapshotArchive.METADATA)
            .updateOne(new Document("_id",original.isolationId()),new Document("$set",new Document("expiresAt",1L)));return target;
        }).when(journal).reconciliationTarget(any(),anyString(),any());
        route(200,"CANCELLED");try(var checked=new ReviewCancellationReconciler(f.mongo,journal,f.client,1,Duration.ofSeconds(5))) {
            assertThrows(IllegalStateException.class,()->checked.check(id,original,"actor",()->true));
        }
        assertEquals(0,reads.get());assertEquals("RESERVED",observations().find().first().getString("state"));
    }
    @Test void statusCanBeObservedAfterTheOriginalCancellationWindowExpires()throws Exception {
        var other=new ReviewOrphanCancellationJournalTest();other.setup();
        try {
            var remote=other.fixture.fixture;var journal=new ReviewOrphanCancellationJournal(remote.mongo,other.resolver,other.fixture.archive,java.time.Clock.systemUTC(),1000);
            var prepared=journal.prepare(other.fixture.prepared.id(),"actor",()->true);var claim=journal.claim(prepared,"actor",()->true);assertNotNull(claim);
            assertTrue(journal.beginDispatch(claim,"actor",()->true)>0);journal.record(claim,"actor",new ReviewOrphanCancellationJournal.Observation("UNKNOWN",null,503),()->true);
            Thread.sleep(1100);assertTrue(System.currentTimeMillis()>=prepared.expiresAt());
            remote.route(e->{assertEquals("GET",e.getRequestMethod());remote.reply(e,200,"CANCELLED");});
            try(var later=new ReviewCancellationReconciler(remote.mongo,journal,remote.client,1,Duration.ofSeconds(5))) {
                var receipt=later.check(UUID.randomUUID().toString(),prepared,"actor",()->true);assertEquals("CANCELLED",receipt.observation().status().state());
                assertEquals("UNKNOWN",journal.receipt(prepared,"actor",()->true).state());assertNull(journal.claim(prepared,"actor",()->true));
            }
        }finally{other.cleanup();}
    }

}
