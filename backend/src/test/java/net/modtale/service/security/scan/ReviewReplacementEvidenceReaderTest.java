package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewReplacementEvidenceReaderTest {
    RemoteReviewIsolationIntegrationTest fixture=new RemoteReviewIsolationIntegrationTest();
    ReviewRemoteTargetReader current;
    ReviewOrphanTargetResolver resolver;
    ReviewReplacementEvidenceReader reader;
    @BeforeEach void setup()throws Exception {
        fixture.setup();new ReviewReplacementAdmissionReader(fixture.fixture.mongo).initialize();current=new ReviewRemoteTargetReader(fixture.fixture.mongo);
        resolver=spy(new ReviewOrphanTargetResolver(fixture.fixture.mongo,fixture.archive,fixture.isolation));
        reader=new ReviewReplacementEvidenceReader(current,resolver,new ReviewReplacementHistoryReader(fixture.fixture.mongo,fixture.archive,current));
    }
    @AfterEach void cleanup(){fixture.cleanup();}
    ReviewReplacementEvidenceReader.Evidence capture(){return reader.capture(fixture.raw().get("_id"),0,"v",()->true);}

    @Test void ordinaryTerminalCaptureDoesNotRequireOrInventIsolationHistory() {
        fixture.fixture.attached(30000);fixture.fixture.change("scanResult.status","CLEAN");fixture.fixture.change("scanResult.scanState","COMPLETED");
        var before=fixture.raw();var evidence=capture();assertNull(evidence.isolation());
        assertEquals(before,fixture.raw());verifyNoInteractions(resolver);
        assertEquals(0,fixture.fixture.gets.get());assertEquals(0,fixture.fixture.posts.get());
    }

    @Test void retainsBothCurrentAndOriginalEvidenceAfterLegitimateMetadataChanges() {
        var claim=fixture.fixture.attached(30000);fixture.fixture.change("scanResult.verdict","BLOCK");fixture.isolateExpiredBrokenPoll();
        fixture.fixture.change("changelog","new moderator context");var before=fixture.raw();
        var evidence=capture();assertEquals(claim.binding(),evidence.current().binding());
        assertEquals(claim.binding(),evidence.isolation().binding());
        assertEquals(fixture.prepared.sha256(),evidence.isolation().beforeSha256());
        assertNotEquals(evidence.current().snapshot().sha256(),evidence.isolation().beforeSha256());
        verify(resolver).resolve(fixture.prepared.id(),"actor");
        assertEquals(before,fixture.raw());assertEquals("BLOCK",fixture.fixture.saved().getVerdict());
        assertEquals(0,fixture.fixture.gets.get());assertEquals(0,fixture.fixture.posts.get());
    }

    @ParameterizedTest @ValueSource(strings={"missing","shape","operation","actor","digest","timestamp","job","receipt","archive"})
    void failsClosedOnBrokenHistoryWithoutChangingOrContactingAnything(String corruption) {
        fixture.fixture.attached(30000);fixture.isolateExpiredBrokenPoll();
        switch(corruption) {
            case "missing" -> fixture.fixture.change("reviewIsolation",null);
            case "shape" -> fixture.fixture.change("reviewIsolation","broken");
            case "operation" -> fixture.fixture.change("reviewIsolation.operationId",UUID.randomUUID().toString());
            case "actor" -> fixture.fixture.change("reviewIsolation.actorId","another");
            case "digest" -> fixture.fixture.change("reviewIsolation.beforeSha256","f".repeat(64));
            case "timestamp" -> fixture.fixture.change("reviewIsolation.isolatedAt",17L);
            case "job" -> fixture.fixture.change("scanResult.remoteReview.jobId",UUID.randomUUID().toString());
            case "receipt" -> fixture.fixture.mongo.getCollection(ReviewRepairJournal.COLLECTION).updateOne(new Document("_id",fixture.prepared.id()),new Document("$set",new Document("state","UNKNOWN")));
            case "archive" -> fixture.fixture.mongo.getCollection(ReviewSnapshotArchive.METADATA).updateOne(new Document("_id",fixture.prepared.id()),new Document("$set",new Document("actorId","another")));
        }
        var before=fixture.raw();assertThrows(RuntimeException.class,this::capture);
        assertEquals(before,fixture.raw());assertEquals(0,fixture.fixture.gets.get());assertEquals(0,fixture.fixture.posts.get());
    }

    @Test void permissionIsRequiredBeforeReadsAndRecheckedAfterHistoricalRecovery() {
        fixture.fixture.attached(30000);fixture.isolateExpiredBrokenPoll();
        var observed=spy(current);var guarded=new ReviewReplacementEvidenceReader(observed,resolver,new ReviewReplacementHistoryReader(fixture.fixture.mongo,fixture.archive,current));
        Object id=fixture.raw().get("_id");
        assertThrows(SecurityException.class,()->guarded.capture(id,0,"v",()->false));
        verifyNoInteractions(observed,resolver);
        var permitted=new AtomicBoolean(true);
        doAnswer(call->{var target=call.callRealMethod();permitted.set(false);return target;}).when(resolver).resolve(fixture.prepared.id(),"actor");
        assertThrows(SecurityException.class,()->guarded.capture(id,0,"v",permitted::get));
        verify(resolver).resolve(fixture.prepared.id(),"actor");
    }

    @ParameterizedTest @ValueSource(strings={"projectType","position"})
    void matchingDisplayIdentityDoesNotOverrideExactHistoricalIdentity(String field) {
        fixture.fixture.attached(30000);fixture.isolateExpiredBrokenPoll();
        var original=resolver.resolve(fixture.prepared.id(),"actor");
        Object differentId=original.projectId() instanceof org.bson.types.ObjectId oid?oid.toHexString():new org.bson.types.ObjectId((String)original.projectId());
        var changed=new ReviewOrphanTargetResolver.Target(original.isolationId(),field.equals("projectType")?differentId:original.projectId(),
                field.equals("position")?1:original.versionIndex(),original.beforeSha256(),original.binding());
        doReturn(changed).when(resolver).resolve(fixture.prepared.id(),"actor");
        assertThrows(IllegalStateException.class,this::capture);
    }
}
