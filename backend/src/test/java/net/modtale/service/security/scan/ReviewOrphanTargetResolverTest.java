package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewOrphanTargetResolverTest {
    RemoteReviewIsolationIntegrationTest fixture=new RemoteReviewIsolationIntegrationTest();
    ReviewOrphanTargetResolver resolver;
    @BeforeEach void setup()throws Exception {
        fixture.setup();resolver=new ReviewOrphanTargetResolver(fixture.fixture.mongo,fixture.archive,fixture.isolation);
    }
    @AfterEach void cleanup(){fixture.cleanup();}
    @Test void usesOriginalAuthenticatedBindingEvenAfterCurrentVersionIsEditedOrDeleted() {
        var claim=fixture.fixture.attached(30000);fixture.isolateExpiredBrokenPoll();var before=fixture.raw();
        var target=resolver.resolve(fixture.prepared.id(),"actor");assertEquals(claim.binding(),target.binding());
        assertEquals(before.get("_id"),target.projectId());assertEquals(0,target.versionIndex());assertEquals(fixture.prepared.sha256(),target.beforeSha256());
        assertEquals(before,fixture.raw());fixture.fixture.change("scanResult.remoteReview.jobId",UUID.randomUUID().toString());
        assertEquals(target,resolver.resolve(fixture.prepared.id(),"actor"));fixture.fixture.mongo.getCollection("projects").deleteMany(new Document());
        assertEquals(target,resolver.resolve(fixture.prepared.id(),"actor"));assertEquals(0,fixture.fixture.gets.get());assertEquals(0,fixture.fixture.posts.get());verifyNoInteractions(fixture.fixture.storage);
    }
    @ParameterizedTest @ValueSource(strings={"projectId","versionId","requestId","attempt","filePath","artifactSha256","contextSha256","manualRescan"})
    void refusesAMismatchedBindingThatIsolationCorrectlyPreserved(String field) {
        fixture.fixture.attached(30000);
        Object value=switch(field){case "attempt"->2;case "manualRescan"->true;case "requestId"->UUID.randomUUID().toString();case "artifactSha256","contextSha256"->"d".repeat(64);default->"different";};
        fixture.fixture.change("scanResult.remoteReview."+field,value);fixture.isolateExpiredBrokenPoll();var before=fixture.raw();
        assertThrows(IllegalStateException.class,()->resolver.resolve(fixture.prepared.id(),"actor"));assertEquals(before,fixture.raw());
    }
    @Test void refusesMissingJobWithoutRemoteDiscovery() {
        fixture.fixture.attached(30000);fixture.fixture.change("scanResult.remoteReview.jobId",null);fixture.isolateExpiredBrokenPoll();
        assertThrows(IllegalStateException.class,()->resolver.resolve(fixture.prepared.id(),"actor"));assertEquals(0,fixture.fixture.gets.get());
    }
    @Test void anotherActorOrAnUnconfirmedIsolationCannotSupplyACancellationTarget() {
        fixture.fixture.attached(30000);fixture.isolateExpiredBrokenPoll();assertThrows(SecurityException.class,()->resolver.resolve(fixture.prepared.id(),"other"));
        fixture.fixture.mongo.getCollection(ReviewRepairJournal.COLLECTION).updateOne(new Document("_id",fixture.prepared.id()),new Document("$set",new Document("state","UNKNOWN")));
        assertThrows(IllegalStateException.class,()->resolver.resolve(fixture.prepared.id(),"actor"));
    }
    @Test void tamperedArchiveCannotSupplyATarget() {
        fixture.fixture.attached(30000);fixture.isolateExpiredBrokenPoll();
        fixture.fixture.mongo.getCollection(ReviewSnapshotArchive.METADATA).updateOne(new Document("_id",fixture.prepared.id()),new Document("$set",new Document("actorId","other")));
        assertThrows(IllegalStateException.class,()->resolver.resolve(fixture.prepared.id(),"actor"));
    }
    @Test void malformedArchivedBindingIsNotReconstructedFromTheCurrentVersion() {
        fixture.fixture.attached(30000);fixture.fixture.change("scanResult.remoteReview",new Document("jobId","invalid"));fixture.isolateExpiredBrokenPoll();
        assertNull(fixture.fixture.saved().getRemoteReview());assertThrows(IllegalStateException.class,()->resolver.resolve(fixture.prepared.id(),"actor"));
    }
    @Test void brokenPollPayloadDoesNotPreventResolvingTheIntactOriginalBinding() {
        var claim=fixture.fixture.attached(30000);fixture.fixture.change("scanResult.remotePoll.token",17);fixture.isolateExpiredBrokenPoll();
        assertEquals(claim.binding(),resolver.resolve(fixture.prepared.id(),"actor").binding());
    }

    @ParameterizedTest @ValueSource(strings={"position","bytes"})
    void archiveReadMustMatchTheSameReceiptEvenIfStorageChangesBetweenReads(String field) {
        fixture.fixture.attached(30000);fixture.isolateExpiredBrokenPoll();var source=fixture.archive.load(fixture.prepared.id());var archive=spy(fixture.archive);
        byte[] bytes=source.versionBytes();if(field.equals("bytes")){var doc=new org.bson.RawBsonDocument(bytes).decode(new org.bson.codecs.DocumentCodec());doc.put("manifestVersion","different");var buffer=new org.bson.RawBsonDocument(doc,new org.bson.codecs.DocumentCodec()).getByteBuffer().asNIO();bytes=new byte[buffer.remaining()];buffer.get(bytes);}
        var changed=new ReviewSnapshotArchive.Snapshot(source.id(),source.projectId(),field.equals("position")?1:source.versionIndex(),source.actorId(),source.action(),source.createdAt(),source.expiresAt(),bytes);
        doReturn(changed).when(archive).load(source.id());
        var resolver=new ReviewOrphanTargetResolver(fixture.fixture.mongo,archive,fixture.isolation);
        assertThrows(IllegalStateException.class,()->resolver.resolve(source.id(),"actor"));
    }

    @Test void legacyBindingWithoutOriginCannotBecomeACancellationTarget() {
        fixture.fixture.attached(30000);fixture.fixture.change("scanResult.remoteReview.origin",null);fixture.isolateExpiredBrokenPoll();
        assertThrows(IllegalStateException.class,()->resolver.resolve(fixture.prepared.id(),"actor"));
    }

}
