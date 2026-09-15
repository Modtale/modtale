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
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewOrphanCancellationJournalTest {
    RemoteReviewIsolationIntegrationTest fixture=new RemoteReviewIsolationIntegrationTest();
    ReviewOrphanTargetResolver resolver;ReviewOrphanCancellationJournal journal;
    @BeforeEach void setup()throws Exception {
        fixture.setup();fixture.fixture.attached(30000);fixture.fixture.change("scanResult.verdict","BLOCK");fixture.isolateExpiredBrokenPoll();
        resolver=new ReviewOrphanTargetResolver(fixture.fixture.mongo,fixture.archive,fixture.isolation);journal=create(fixture.fixture.mongo,Clock.systemUTC());
    }
    @AfterEach void cleanup(){fixture.cleanup();}
    ReviewOrphanCancellationJournal create(MongoTemplate mongo,Clock clock){return new ReviewOrphanCancellationJournal(mongo,resolver,fixture.archive,clock,60000);}
    ReviewOrphanCancellationJournal.Prepared prepare(){return journal.prepare(fixture.prepared.id(),"actor",()->true);}
    MongoCollection<Document> operations(){return fixture.fixture.mongo.getCollection(ReviewOrphanCancellationJournal.COLLECTION);}
    @Test void preparationRetainsOriginalTargetAndExpiryAcrossRestartWithoutRemoteCalls() {
        var before=fixture.raw();var prepared=prepare();var record=operations().find().first();
        assertEquals(prepared,create(fixture.fixture.mongo,Clock.systemUTC()).prepare(fixture.prepared.id(),"actor",()->true));assertEquals(record,operations().find().first());
        assertEquals(fixture.prepared.id(),prepared.isolationId());assertTrue(prepared.targetSha256().matches("[0-9a-f]{64}"));
        fixture.fixture.change("scanResult.remoteReview.jobId",UUID.randomUUID().toString());assertEquals(prepared,prepare());
        assertEquals("BLOCK",fixture.fixture.saved().getVerdict());assertEquals(before.get("reviewStatus"),fixture.raw().get("reviewStatus"));
        assertEquals(0,fixture.fixture.gets.get());assertEquals(0,fixture.fixture.posts.get());verifyNoInteractions(fixture.fixture.storage);
    }
    @Test void concurrentCallersCanObtainOnlyOneExecutionToken()throws Exception {
        var prepared=prepare();var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(()->{start.await();return journal.claim(prepared,"actor",()->true);});
            var two=pool.submit(()->{start.await();return create(fixture.fixture.mongo,Clock.systemUTC()).claim(prepared,"actor",()->true);});start.countDown();
            var a=one.get(10,TimeUnit.SECONDS);var b=two.get(10,TimeUnit.SECONDS);assertNotEquals(a==null,b==null);
            var winner=a==null?b:a;assertEquals(fixture.fixture.job,winner.target().binding().jobId());assertNotNull(winner.target().binding().origin());
        }
        assertNull(journal.claim(prepared,"actor",()->true));assertEquals(1,operations().countDocuments());assertEquals("EXECUTING",operations().find().first().getString("state"));
    }
    @Test void databaseTimeRejectsExpiredOrFutureIntentEvenWithSkewedClientClock() {
        var old=create(fixture.fixture.mongo,Clock.fixed(Instant.now().minusSeconds(3600),ZoneOffset.UTC));var prepared=old.prepare(fixture.prepared.id(),"actor",()->true);
        assertNull(old.claim(prepared,"actor",()->true));assertEquals("PREPARED",operations().find().first().getString("state"));
        assertThrows(IllegalStateException.class,()->prepare());
    }
    @Test void futureIntentCannotExecute() {
        var future=create(fixture.fixture.mongo,Clock.fixed(Instant.now().plusSeconds(3600),ZoneOffset.UTC));var prepared=future.prepare(fixture.prepared.id(),"actor",()->true);
        assertNull(future.claim(prepared,"actor",()->true));
    }
    @ParameterizedTest @ValueSource(strings={"actor","target","expiry","extra","state","numericType"})
    void alteredStoredIntentOrStateCannotAcquireAuthority(String field) {
        var prepared=prepare();var change=switch(field) {
            case "actor"->new Document("intent.actor","other");case "target"->new Document("intent.target.binding.origin.deploymentId","22222222-2222-2222-2222-222222222222");
            case "expiry"->new Document("intent.expiresAt",prepared.expiresAt()+1);case "extra"->new Document("extra",true);case "numericType"->new Document("intent.target.binding.attempt",1L);
            default->new Document("state",List.of("PREPARED"));};
        operations().updateOne(new Document("_id",prepared.isolationId()),new Document("$set",change));var before=operations().find().first();
        assertThrows(IllegalStateException.class,()->journal.claim(prepared,"actor",()->true));assertEquals(before,operations().find().first());
    }
    @Test void permissionAndArchiveAuthorityAreRequiredBeforeCreatingIntent() {
        assertThrows(SecurityException.class,()->journal.prepare(fixture.prepared.id(),"actor",()->false));assertThrows(SecurityException.class,()->journal.prepare(fixture.prepared.id(),"other",()->true));
        fixture.fixture.mongo.getCollection(ReviewRepairJournal.COLLECTION).updateOne(new Document("_id",fixture.prepared.id()),new Document("$set",new Document("state","UNKNOWN")));
        assertThrows(IllegalStateException.class,()->prepare());assertEquals(0,operations().countDocuments());
    }
    MongoCollection<Document> intercepted(MongoTemplate template) {
        var configured=operations().withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY).withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        var spy=spy(configured);doReturn(spy).when(template).getCollection(ReviewOrphanCancellationJournal.COLLECTION);
        doReturn(spy).when(spy).withReadPreference(any());doReturn(spy).when(spy).withReadConcern(any());doReturn(spy).when(spy).withWriteConcern(any());doReturn(spy).when(spy).withTimeout(anyLong(),any());return spy;
    }
    @Test void lostPreparationAcknowledgementRetainsOneOriginalIntent() {
        var mongo=spy(fixture.fixture.mongo);var ops=intercepted(mongo);doAnswer(i->{i.callRealMethod();throw new MongoException("Lost preparation acknowledgement");}).when(ops).insertOne(any(Document.class));
        var prepared=create(mongo,Clock.systemUTC()).prepare(fixture.prepared.id(),"actor",()->true);assertEquals(prepared,prepare());assertEquals(1,operations().countDocuments());
    }
    @Test void lostClaimAcknowledgementRecoversOnlyThisInvocationsToken() {
        var prepared=prepare();var mongo=spy(fixture.fixture.mongo);var ops=intercepted(mongo);
        doAnswer(i->{i.callRealMethod();throw new MongoException("Lost claim acknowledgement");}).when(ops).findOneAndUpdate(any(org.bson.conversions.Bson.class),anyList(),any(com.mongodb.client.model.FindOneAndUpdateOptions.class));
        var claim=create(mongo,Clock.systemUTC()).claim(prepared,"actor",()->true);assertNotNull(claim);assertEquals(claim.token(),operations().find().first().getString("token"));assertNull(journal.claim(prepared,"actor",()->true));
    }
    @Test void revokedPermissionAfterArmingCannotExposeOrReclaimToken() {
        var prepared=prepare();var permitted=new AtomicBoolean(true);var mongo=spy(fixture.fixture.mongo);var ops=intercepted(mongo);
        doAnswer(i->{var result=i.callRealMethod();permitted.set(false);return result;}).when(ops).findOneAndUpdate(any(org.bson.conversions.Bson.class),anyList(),any(com.mongodb.client.model.FindOneAndUpdateOptions.class));
        assertThrows(SecurityException.class,()->create(mongo,Clock.systemUTC()).claim(prepared,"actor",permitted::get));assertEquals("EXECUTING",operations().find().first().getString("state"));assertNull(journal.claim(prepared,"actor",()->true));
    }
    @Test void anUncommittedFailedClaimNeverReturnsExecutionAuthority() {
        var prepared=prepare();var mongo=spy(fixture.fixture.mongo);var ops=intercepted(mongo);
        doThrow(new MongoException("Claim unavailable")).when(ops).findOneAndUpdate(any(org.bson.conversions.Bson.class),anyList(),any(com.mongodb.client.model.FindOneAndUpdateOptions.class));
        assertThrows(IllegalStateException.class,()->create(mongo,Clock.systemUTC()).claim(prepared,"actor",()->true));assertEquals("PREPARED",operations().find().first().getString("state"));
    }
    @ParameterizedTest @ValueSource(strings={"attempt","versionIndex","state"})
    void aTypeSubstitutionAfterValidationCannotWinTheClaim(String field) {
        var prepared=prepare();var mongo=spy(fixture.fixture.mongo);var ops=intercepted(mongo);
        doAnswer(i->{
            var patch=switch(field){case "attempt"->new Document("intent.target.binding.attempt",1L);case "versionIndex"->new Document("intent.target.versionIndex",0L);default->new Document("state",List.of("PREPARED"));};
            operations().updateOne(new Document("_id",prepared.isolationId()),new Document("$set",patch));return i.callRealMethod();
        }).when(ops).findOneAndUpdate(any(org.bson.conversions.Bson.class),anyList(),any(com.mongodb.client.model.FindOneAndUpdateOptions.class));
        assertNull(create(mongo,Clock.systemUTC()).claim(prepared,"actor",()->true));assertFalse(operations().find().first().containsKey("token"));
    }

    @Test void alteredJournalTimesCannotRenewPreparationAgainstTheSignedArchive() {
        var prepared=prepare();operations().updateOne(new Document("_id",prepared.isolationId()),new Document("$set",new Document("intent.createdAt",prepared.createdAt()+1).append("intent.expiresAt",prepared.expiresAt()+1)));
        assertThrows(IllegalStateException.class,()->prepare());
        var forged=new ReviewOrphanCancellationJournal.Prepared(prepared.isolationId(),prepared.targetSha256(),prepared.createdAt()+1,prepared.expiresAt()+1);
        assertThrows(IllegalStateException.class,()->journal.claim(forged,"actor",()->true));assertFalse(operations().find().first().containsKey("token"));
    }
    @Test void aTamperedCancellationArchiveCannotAuthorizeAClaim() {
        var prepared=prepare();fixture.fixture.mongo.getCollection(ReviewSnapshotArchive.METADATA).updateOne(new Document("action","CANCEL_REMOTE_REVIEW"),new Document("$set",new Document("expiresAt",prepared.expiresAt()+1)));
        assertThrows(IllegalStateException.class,()->journal.claim(prepared,"actor",()->true));assertFalse(operations().find().first().containsKey("token"));
    }
    @Test void archivedIntentSurvivesJournalInsertFailureWithoutRenewingExpiry() {
        var mongo=spy(fixture.fixture.mongo);var ops=intercepted(mongo);doThrow(new MongoException("No journal acknowledgement")).when(ops).insertOne(any(Document.class));
        assertThrows(IllegalStateException.class,()->create(mongo,Clock.systemUTC()).prepare(fixture.prepared.id(),"actor",()->true));
        var retained=fixture.fixture.mongo.getCollection(ReviewSnapshotArchive.METADATA).find(new Document("action","CANCEL_REMOTE_REVIEW")).first();assertNotNull(retained);
        var prepared=prepare();assertEquals(retained.getLong("createdAt"),prepared.createdAt());assertEquals(retained.getLong("expiresAt"),prepared.expiresAt());
    }

}
