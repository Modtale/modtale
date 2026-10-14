package net.modtale.service.admin.review;

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

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class ReviewRepairJournalTest {
    MongoClient client;MongoTemplate mongo;ReviewSnapshotArchive archive;ReviewRepairJournal journal;ReviewRepairPreparation.Prepared prepared;
    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException();
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");mongo=new MongoTemplate(client,"warden_repair_journal_"+UUID.randomUUID().toString().replace("-",""));
        mongo.getCollection("projects").insertOne(new Document("_id","p").append("versions",List.of(new Document("_id","v").append("scanResult","malformed"))));
        archive=new ReviewSnapshotArchive(mongo,"test",Map.of("test",new byte[32]));var reader=new RawReviewSnapshotReader(mongo);
        var request=new ReviewRepairPreparation.Request(UUID.randomUUID().toString(),"p",0,"v",reader.capture("p",0,"v").sha256(),"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW);
        prepared=new ReviewRepairPreparation(archive,reader,Clock.systemUTC(),1,60000).prepare(request,()->true);journal=new ReviewRepairJournal(mongo,archive);
    }
    @AfterEach void cleanup(){mongo.getDb().drop();client.close();}
    ReviewRepairJournal.Claim claim(ReviewRepairJournal j){return j.claim(prepared,"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW,()->true);}
    @Test void restartCannotReclaimExecutingOrUncertainOperation() {
        var before=mongo.getCollection("projects").find().first();var first=claim(journal);assertNotNull(first);
        assertNull(claim(new ReviewRepairJournal(mongo,archive)));assertTrue(journal.markUnknown(first,()->true));assertTrue(journal.markUnknown(first,()->true));
        assertNull(claim(journal));assertFalse(journal.markUnknown(new ReviewRepairJournal.Claim(first.id(),UUID.randomUUID().toString()),()->true));
        assertEquals(before,mongo.getCollection("projects").find().first());
    }
    @Test void concurrentClaimantsHaveOnlyOneWinner()throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->claim(new ReviewRepairJournal(mongo,archive)));var b=executor.submit(()->claim(new ReviewRepairJournal(mongo,archive)));
            var first=a.get(5,TimeUnit.SECONDS);var second=b.get(5,TimeUnit.SECONDS);assertNotEquals(first==null,second==null);
        }
        assertEquals(1,mongo.getCollection(ReviewRepairJournal.COLLECTION).countDocuments());
    }
    @Test void expiredPreparedEvidenceCannotArmExecution() {
        var old=archive.load(prepared.id());var expired=new ReviewSnapshotArchive.Snapshot(UUID.randomUUID().toString(),old.projectId(),old.versionIndex(),old.actorId(),old.action(),1,2,old.versionBytes());archive.retain(expired);
        prepared=new ReviewRepairPreparation.Prepared(expired.id(),prepared.sha256(),1,2);assertNull(claim(journal));
        assertEquals("RESERVED",mongo.getCollection(ReviewRepairJournal.COLLECTION).find().first().getString("state"));assertNull(claim(journal));
    }
    @ParameterizedTest @ValueSource(strings={"actor","action","hash","expiry"})
    void originalArchiveBindingCannotBeForged(String mutation) {
        var request=mutation.equals("hash")?new ReviewRepairPreparation.Prepared(prepared.id(),"a".repeat(64),prepared.createdAt(),prepared.expiresAt()):
                mutation.equals("expiry")?new ReviewRepairPreparation.Prepared(prepared.id(),prepared.sha256(),prepared.createdAt(),prepared.expiresAt()+1):prepared;
        assertThrows(IllegalStateException.class,()->journal.claim(request,mutation.equals("actor")?"other":"actor",mutation.equals("action")?ReviewSnapshotArchive.Action.REPLACE_REVIEW:ReviewSnapshotArchive.Action.ISOLATE_REVIEW,()->true));
        assertEquals(0,mongo.getCollection(ReviewRepairJournal.COLLECTION).countDocuments());
    }
    MongoCollection<Document> intercepted(MongoTemplate template) {
        var configured=mongo.getCollection(ReviewRepairJournal.COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY).withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        var spy=spy(configured);doReturn(spy).when(template).getCollection(ReviewRepairJournal.COLLECTION);doReturn(spy).when(spy).withReadPreference(any());doReturn(spy).when(spy).withReadConcern(any());doReturn(spy).when(spy).withWriteConcern(any());return spy;
    }
    @Test void committedReservationWithLostAcknowledgementCanArmOnlyItsOwnToken() {
        var template=spy(mongo);var ops=intercepted(template);doAnswer(i->{i.callRealMethod();throw new MongoException("lost acknowledgement");}).when(ops).insertOne(any(Document.class));
        var result=claim(new ReviewRepairJournal(template,archive));assertNotNull(result);assertNull(claim(journal));
    }
    @Test void committedArmingWithLostAcknowledgementRecoversOwnClaim() {
        var template=spy(mongo);var ops=intercepted(template);
        doAnswer(i->{i.callRealMethod();throw new MongoException("lost acknowledgement");}).when(ops).findOneAndUpdate(any(org.bson.conversions.Bson.class),anyList(),any(com.mongodb.client.model.FindOneAndUpdateOptions.class));
        var result=claim(new ReviewRepairJournal(template,archive));assertNotNull(result);assertNull(claim(journal));
    }
    @Test void unknownOutcomeWithLostAcknowledgementIsDurableAndNeverReclaimed() {
        var own=claim(journal);assertNotNull(own);var template=spy(mongo);var ops=intercepted(template);
        doAnswer(i->{i.callRealMethod();throw new MongoException("lost acknowledgement");}).when(ops).updateOne(any(org.bson.conversions.Bson.class),anyList(),any(com.mongodb.client.model.UpdateOptions.class));
        assertTrue(new ReviewRepairJournal(template,archive).markUnknown(own,()->true));assertNull(claim(journal));
        assertEquals("UNKNOWN",mongo.getCollection(ReviewRepairJournal.COLLECTION).find().first().getString("state"));
    }
    @Test void revocationAfterReservationLeavesNoReusableExecutionAuthority() {
        var allowed=new AtomicBoolean(true);var template=spy(mongo);var ops=intercepted(template);
        doAnswer(i->{var result=i.callRealMethod();allowed.set(false);return result;}).when(ops).insertOne(any(Document.class));
        assertThrows(SecurityException.class,()->new ReviewRepairJournal(template,archive).claim(prepared,"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW,allowed::get));
        assertEquals("RESERVED",mongo.getCollection(ReviewRepairJournal.COLLECTION).find().first().getString("state"));assertNull(claim(journal));
    }
}
