package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewIsolationExecutorTest {
    MongoClient client;MongoTemplate mongo;MongoCollection<Document> projects;RawReviewSnapshotReader reader;ReviewSnapshotArchive archive;ReviewRepairJournal journal;
    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REPAIR_TX_DB_PORT","27031");if(!Set.of("27031","27032").contains(port))throw new IllegalArgumentException();
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?directConnection=true&serverSelectionTimeoutMS=3000&socketTimeoutMS=10000");
        mongo=new MongoTemplate(client,"warden_isolate_"+UUID.randomUUID().toString().replace("-",""));projects=mongo.getCollection("projects");
        var scan=new Document("status","SCANNING").append("scanState","REMOTE_REVIEW").append("verdict","BLOCK").append("riskScore",73)
                .append("issues",List.of(new Document("severity","HIGH").append("description","Retained finding").append("resolved",false)))
                .append("scanRequestId",UUID.randomUUID().toString()).append("scanAttempt",1).append("manualRescan",true).append("artifactVerified",true);
        var version=new Document("_id","v").append("reviewStatus","PENDING").append("scanResult",scan).append("securityApprovedAt",123L)
                .append("scheduledPublishDate",123L).append("approvedSecurityContextSha256","a".repeat(64)).append("unknownHistory",List.of("retain"));
        projects.insertOne(new Document("_id","p").append("versions",List.of(version)));
        reader=new RawReviewSnapshotReader(mongo);archive=new ReviewSnapshotArchive(mongo,"test",Map.of("test",new byte[32]));journal=new ReviewRepairJournal(mongo,archive);
    }
    @AfterEach void cleanup(){mongo.getDb().drop();client.close();}
    ReviewRepairPreparation.Prepared prepare() {
        var req=new ReviewRepairPreparation.Request(UUID.randomUUID().toString(),"p",0,"v",reader.capture("p",0,"v").sha256(),"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW);
        return new ReviewRepairPreparation(archive,reader,Clock.systemUTC(),1,60000).prepare(req,()->true);
    }
    ReviewIsolationExecutor executor(){return new ReviewIsolationExecutor(mongo,archive,reader,journal);}
    Document version(){return projects.find().first().getList("versions",Document.class).getFirst();}
    Document operation(){return mongo.getCollection(ReviewRepairJournal.COLLECTION).find().first();}
    void set(String path,Object value){projects.updateOne(new Document("_id","p"),new Document("$set",new Document("versions.0."+path,value)));}
    @Test void isolatesWithAtomicReceiptPreservedEvidenceAndNoSecurityClearance() {
        var before=reader.capture("p",0,"v");var prepared=prepare();var result=executor().execute(prepared,"actor",()->true);
        assertEquals("APPLIED",result.state());var v=version();var scan=v.get("scanResult",Document.class);
        assertEquals("PENDING",v.get("reviewStatus"));assertEquals("FAILED",scan.get("status"));assertEquals("REMOTE_ISOLATED",scan.get("scanState"));
        assertEquals("BLOCK",scan.get("verdict"));assertEquals(73,scan.get("riskScore"));assertEquals(false,scan.get("artifactVerified"));
        assertEquals(new org.bson.RawBsonDocument(before.versionBytes()).decode(new org.bson.codecs.DocumentCodec()).get("scanResult",Document.class).get("issues"),scan.get("issues"));
        assertEquals(List.of("retain"),v.get("unknownHistory"));assertNull(v.get("scheduledPublishDate"));assertEquals(0L,v.get("securityApprovedAt"));assertNull(v.get("approvedSecurityContextSha256"));
        assertEquals(prepared.id(),v.get("reviewIsolation",Document.class).get("operationId"));assertInstanceOf(Date.class,v.get("reviewIsolation",Document.class).get("isolatedAt"));
        assertArrayEquals(before.versionBytes(),archive.load(prepared.id()).versionBytes());assertEquals(reader.capture("p",0,"v").sha256(),result.afterSha256());assertEquals("APPLIED",operation().get("state"));
        assertEquals(result,executor().execute(prepared,"actor",()->true));assertEquals(v,version());
    }
    @Test void healthyQueuedReviewIsIneligibleWithoutClaim() {
        set("scanResult.scanState","QUEUED");var before=version();assertEquals("INELIGIBLE",executor().execute(prepare(),"actor",()->true).state());assertNull(operation());assertEquals(before,version());
    }
    @Test void unreadableRemainingStateCannotDisappearFromDiagnostics() {
        set("scanResult.manualRescan",null);var before=version();assertEquals("INELIGIBLE",executor().execute(prepare(),"actor",()->true).state());assertNull(operation());assertEquals(before,version());
    }
    @Test void activeLeasePreventsMutationEvenWhenPollShapeIsBroken() {
        set("scanResult.remotePoll",new Document("leaseUntil",new Date(System.currentTimeMillis()+120000)));var before=version();
        assertEquals("NOT_APPLIED",executor().execute(prepare(),"actor",()->true).state());assertEquals(before,version());assertEquals("NOT_APPLIED",operation().get("state"));
    }
    @Test void changedBytesSincePreparationDoNotApply() {
        var prepared=prepare();set("scanResult.scanAttempt",1L);var before=version();assertEquals("NOT_APPLIED",executor().execute(prepared,"actor",()->true).state());assertEquals(before,version());
    }
    @Test void concurrentEditAfterSnapshotAbortsBothWrites() {
        var prepared=prepare();var intercepted=spy(reader);var once=new AtomicBoolean();
        doAnswer(i->{var result=i.callRealMethod();if(once.compareAndSet(false,true))set("scanResult.scanAttempt",1L);return result;}).when(intercepted).capture(any(ClientSession.class),eq("p"),eq(0),eq("v"));
        var result=new ReviewIsolationExecutor(mongo,archive,intercepted,journal).execute(prepared,"actor",()->true);
        assertEquals("UNKNOWN",result.state());assertEquals("SCANNING",version().get("scanResult",Document.class).get("status"));assertEquals(1L,version().get("scanResult",Document.class).get("scanAttempt"));assertEquals("UNKNOWN",operation().get("state"));
    }
    @Test void permissionRevokedAfterVersionWriteRollsBackEverything() {
        var prepared=prepare();var before=version();var allowed=new AtomicBoolean(true);var intercepted=spy(reader);
        doAnswer(i->{var result=(RawReviewSnapshotReader.Captured)i.callRealMethod();if(!result.sha256().equals(prepared.sha256()))allowed.set(false);return result;}).when(intercepted).capture(any(ClientSession.class),eq("p"),eq(0),eq("v"));
        assertThrows(SecurityException.class,()->new ReviewIsolationExecutor(mongo,archive,intercepted,journal).execute(prepared,"actor",allowed::get));
        assertEquals(before,version());assertEquals("UNKNOWN",operation().get("state"));
    }
    @Test void lostCommitAcknowledgementReconcilesWithoutRepeatingMutation() {
        var prepared=prepare();var wrapped=spy(mongo);var factory=spy(mongo.getMongoDatabaseFactory());doReturn(factory).when(wrapped).getMongoDatabaseFactory();
        doAnswer(i->{var session=spy((ClientSession)i.callRealMethod());doAnswer(c->{c.callRealMethod();var lost=new MongoException("lost commit acknowledgement");lost.addLabel("UnknownTransactionCommitResult");throw lost;}).when(session).commitTransaction();return session;}).when(factory).getSession(any(ClientSessionOptions.class));
        var result=new ReviewIsolationExecutor(wrapped,archive,reader,journal).execute(prepared,"actor",()->true);assertEquals("APPLIED",result.state());assertEquals(reader.capture("p",0,"v").sha256(),result.afterSha256());assertEquals(result,executor().execute(prepared,"actor",()->true));
    }
    @Test void malformedBindingIsArchivedBeforeDetachment() {
        set("scanResult.remoteReview",new Document("jobId","unreadable"));var prepared=prepare();var original=archive.load(prepared.id()).versionBytes();
        assertEquals("APPLIED",executor().execute(prepared,"actor",()->true).state());assertNull(version().get("scanResult",Document.class).get("remoteReview"));assertArrayEquals(original,archive.load(prepared.id()).versionBytes());
    }
    @Test void validMismatchedBindingRemainsAvailableForReconciliation() {
        var binding=new Document("projectId","other").append("versionId","v").append("requestId",UUID.randomUUID().toString()).append("attempt",1)
                .append("filePath","mods/example.jar").append("artifactSha256","a".repeat(64)).append("contextSha256","b".repeat(64))
                .append("policyVersion","warden-3.0.0:"+"c".repeat(64)).append("reviewConfigSha256","d".repeat(64)).append("jobId",UUID.randomUUID().toString()).append("manualRescan",true);
        set("scanResult.remoteReview",binding);assertEquals("APPLIED",executor().execute(prepare(),"actor",()->true).state());assertEquals(binding,version().get("scanResult",Document.class).get("remoteReview"));
    }
    @Test void expiredIntentCannotChangeVersion() {
        var capture=reader.capture("p",0,"v");var now=System.currentTimeMillis();var id=UUID.randomUUID().toString();
        archive.retain(capture.forArchive(id,"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW,now-20000,now-10000));
        var prepared=new ReviewRepairPreparation.Prepared(id,capture.sha256(),now-20000,now-10000);var before=version();
        assertEquals("UNKNOWN",executor().execute(prepared,"actor",()->true).state());assertEquals(before,version());assertEquals("RESERVED",operation().get("state"));
    }
    @Test void duplicateVersionIntroducedAfterPreparationCannotBeIsolated() {
        var prepared=prepare();projects.updateOne(new Document("_id","p"),new Document("$push",new Document("versions",version())));var before=projects.find().first();
        assertEquals("UNKNOWN",executor().execute(prepared,"actor",()->true).state());assertEquals(before,projects.find().first());assertEquals("UNKNOWN",operation().get("state"));
    }
    @Test void standaloneTopologyIsRejectedBeforeClaim() {
        var prepared=prepare();var wrapped=spy(mongo);var db=spy(mongo.getDb());doReturn(db).when(wrapped).getDb();
        doReturn(new Document("isWritablePrimary",true)).when(db).runCommand(eq(new Document("hello",1)),eq(ReadPreference.primary()));
        var before=version();assertThrows(IllegalStateException.class,()->new ReviewIsolationExecutor(wrapped,archive,reader,journal).execute(prepared,"actor",()->true));assertEquals(before,version());assertNull(operation());
    }
    @Test void wrongActorCannotAccessOrClaimRepair() {
        var prepared=prepare();assertThrows(SecurityException.class,()->executor().execute(prepared,"other",()->true));assertNull(operation());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"shutdown","deadline"})
    void workflowCancellationAfterVersionWriteAbortsRepair(String mode) {
        var prepared=prepare();var before=version();var intercepted=spy(reader);var ticks=new java.util.concurrent.atomic.AtomicLong();
        var preparation=new ReviewRepairPreparation(archive,reader,Clock.systemUTC(),1,60000);
        var workflow=new ReviewRepairWorkflow(preparation,new ReviewIsolationExecutor(mongo,archive,intercepted,journal),1,1000,ticks::get);
        doAnswer(i->{var result=(RawReviewSnapshotReader.Captured)i.callRealMethod();if(!result.sha256().equals(prepared.sha256())) {
            if(mode.equals("shutdown"))workflow.close();else ticks.addAndGet(1000000000L);
        }return result;}).when(intercepted).capture(any(ClientSession.class),eq("p"),eq(0),eq("v"));
        assertEquals("UNKNOWN",workflow.isolate(prepared,"actor",()->true).state());assertEquals(before,version());assertEquals("UNKNOWN",operation().get("state"));
        assertEquals(new ReviewRepairWorkflow.Status(mode.equals("shutdown")?"CLOSED":"OPEN",0),workflow.status());
    }

}
