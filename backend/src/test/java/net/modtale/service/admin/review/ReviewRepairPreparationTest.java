package net.modtale.service.admin.review;

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
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class ReviewRepairPreparationTest {
    MongoClient client;MongoTemplate mongo;RawReviewSnapshotReader reader;ReviewSnapshotArchive archive;Clock clock;
    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException();
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000");mongo=new MongoTemplate(client,"warden_prepare_"+UUID.randomUUID().toString().replace("-",""));
        mongo.getCollection("projects").insertOne(new Document("_id","p").append("versions",List.of(new Document("_id","v").append("scanResult","malformed"))));
        reader=new RawReviewSnapshotReader(mongo);archive=new ReviewSnapshotArchive(mongo,"test",Map.of("test",new byte[32]));clock=Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC);
    }
    @AfterEach void cleanup(){mongo.getDb().drop();client.close();}
    ReviewRepairPreparation.Request request(){return new ReviewRepairPreparation.Request(UUID.randomUUID().toString(),"p",0,"v",reader.capture("p",0,"v").sha256(),"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW);}
    ReviewRepairPreparation service(ReviewSnapshotArchive a,RawReviewSnapshotReader r,Clock c){return new ReviewRepairPreparation(a,r,c,1,1000);}
    @Test void retryAfterRestartUsesOriginalSnapshotAndExpiryWithoutProjectMutation() {
        var req=request();var before=mongo.getCollection("projects").find().first();var result=service(archive,reader,clock).prepare(req,()->true);
        var restarted=service(new ReviewSnapshotArchive(mongo,"test",Map.of("test",new byte[32])),new RawReviewSnapshotReader(mongo),Clock.fixed(Instant.ofEpochMilli(1500),ZoneOffset.UTC));
        assertEquals(result,restarted.prepare(req,()->true));assertEquals(2000,result.expiresAt());assertEquals(1,mongo.getCollection(ReviewSnapshotArchive.METADATA).countDocuments());assertEquals(before,mongo.getCollection("projects").find().first());
    }
    @ParameterizedTest @ValueSource(strings={"actor","action","project","hash"})
    void sameRequestIdentityCannotBeRebound(String field) {
        var req=request();service(archive,reader,clock).prepare(req,()->true);
        var replacement=new ReviewRepairPreparation.Request(req.id(),field.equals("project")?"other":req.projectId(),0,"v",field.equals("hash")?"a".repeat(64):req.expectedSha256(),field.equals("actor")?"other":req.actorId(),field.equals("action")?ReviewSnapshotArchive.Action.REPLACE_REVIEW:req.action());
        assertThrows(IllegalStateException.class,()->service(archive,reader,clock).prepare(replacement,()->true));assertEquals("actor",archive.load(req.id()).actorId());
    }
    @Test void expiredIntentCannotBeRenewedByRetry() {
        var req=request();service(archive,reader,clock).prepare(req,()->true);
        assertThrows(IllegalStateException.class,()->service(archive,reader,Clock.fixed(Instant.ofEpochMilli(2000),ZoneOffset.UTC)).prepare(req,()->true));assertEquals(2000,archive.load(req.id()).expiresAt());
    }
    @Test void staleExpectedDigestCannotArchiveNewerState() {
        var req=request();mongo.getCollection("projects").updateOne(new Document(),new Document("$set",new Document("versions.0.changed",true)));
        assertThrows(IllegalStateException.class,()->service(archive,reader,clock).prepare(req,()->true));assertEquals(0,mongo.getCollection(ReviewSnapshotArchive.METADATA).countDocuments());
    }
    @Test void changeAfterArchivalRetainsEvidenceButReturnsNoPreparedResult() {
        var req=request();var intercepted=spy(archive);doAnswer(i->{var result=i.callRealMethod();mongo.getCollection("projects").updateOne(new Document(),new Document("$set",new Document("versions.0.changed",true)));return result;}).when(intercepted).retain(any());
        assertThrows(IllegalStateException.class,()->service(intercepted,reader,clock).prepare(req,()->true));assertNotNull(archive.load(req.id()));
        assertThrows(IllegalStateException.class,()->service(archive,reader,clock).prepare(req,()->true));
    }
    @Test void permissionLossAfterArchiveCannotReturnPreparedResult() {
        var req=request();var allowed=new AtomicBoolean(true);var intercepted=spy(archive);
        doAnswer(i->{var result=i.callRealMethod();allowed.set(false);return result;}).when(intercepted).retain(any());
        assertThrows(SecurityException.class,()->service(intercepted,reader,clock).prepare(req,allowed::get));assertNotNull(archive.load(req.id()));
        var a=mock(ReviewSnapshotArchive.class);var r=mock(RawReviewSnapshotReader.class);
        assertThrows(SecurityException.class,()->service(a,r,clock).prepare(req,()->false));verifyNoInteractions(a,r);
    }
    @Test void unknownReturnAfterArchiveCommitRecoversSameIntent() {
        var req=request();var intercepted=spy(archive);doAnswer(i->{i.callRealMethod();throw new IllegalStateException("lost response");}).when(intercepted).retain(any());
        assertThrows(IllegalStateException.class,()->service(intercepted,reader,clock).prepare(req,()->true));
        assertEquals(req.id(),service(archive,reader,clock).prepare(req,()->true).id());assertEquals(1,mongo.getCollection(ReviewSnapshotArchive.METADATA).countDocuments());
    }
    @Test void localAdmissionHasNoUnboundedWaitQueue()throws Exception {
        var req=request();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var intercepted=spy(archive);
        doAnswer(i->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return i.callRealMethod();}).when(intercepted).find(req.id());
        var service=service(intercepted,reader,clock);try(var executor=Executors.newSingleThreadExecutor()) {
            var running=executor.submit(()->service.prepare(req,()->true));try {assertTrue(entered.await(5,TimeUnit.SECONDS));assertThrows(IllegalStateException.class,()->service.prepare(req,()->true));}finally{release.countDown();}
            assertEquals(req.id(),running.get(5,TimeUnit.SECONDS).id());
        }
        assertEquals(req.id(),service.prepare(req,()->true).id());
    }
}
