package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewReplacementHistoryReaderTest {
    ReviewReplacementPreparationTest fixture=new ReviewReplacementPreparationTest();
    ReviewReplacementHistoryReader history;
    ReviewReplacementPreparation.Prepared first;
    @BeforeEach void setup()throws Exception {
        fixture.setup();history=new ReviewReplacementHistoryReader(fixture.fixture.fixture.mongo,fixture.fixture.archive,fixture.targets);
        first=stage();
        // Admission/remote execution is outside this fixture: simulate its terminal stored binding explicitly.
        terminal();
    }
    @AfterEach void cleanup(){fixture.cleanup();}
    ReviewReplacementPreparation.Prepared stage() {
        var prepared=fixture.preparation.prepare(fixture.request(),()->true);
        var executor=new ReviewReplacementExecutor(fixture.fixture.fixture.mongo,fixture.preparation,fixture.fixture.reader,
                new ReviewRepairJournal(fixture.fixture.fixture.mongo,fixture.fixture.archive));
        assertEquals("APPLIED",executor.stage(prepared,"new-moderator",()->true).state());return prepared;
    }
    void terminal() {
        var f=fixture.fixture.fixture;f.change("scanResult.remoteReview.jobId",UUID.randomUUID().toString());
        f.change("scanResult.status","INFECTED");f.change("scanResult.scanState","COMPLETED");f.change("scanResult.verdict","BLOCK");
    }
    ReviewReplacementEvidenceReader.Evidence evidence() {
        return fixture.evidence.capture(fixture.fixture.raw().get("_id"),0,"v",()->true);
    }

    @Test void laterGenerationsRetainPreviousSignedReferencesWithoutEmbeddingAnArray() {
        var firstLink=evidence().previousReplacement();assertEquals(first.id(),firstLink.operationId());assertNull(firstLink.previousOperationId());
        var second=stage();terminal();var secondLink=evidence().previousReplacement();
        assertEquals(second.id(),secondLink.operationId());assertEquals(first.id(),secondLink.previousOperationId());
        var beforeSecond=new org.bson.RawBsonDocument(fixture.fixture.archive.load(second.beforeArchiveId()).versionBytes()).decode(new org.bson.codecs.DocumentCodec());
        assertEquals(first.id(),beforeSecond.get("reviewReplacement",Document.class).get("operationId"));
        assertEquals("BLOCK",beforeSecond.get("scanResult",Document.class).get("verdict"));
        assertEquals(3,fixture.fixture.raw().getList("versions",Document.class).getFirst().get("reviewReplacement",Document.class).size());
        assertEquals(0,fixture.fixture.fixture.gets.get());assertEquals(0,fixture.fixture.fixture.posts.get());
    }

    @ParameterizedTest @ValueSource(strings={"missing","null","shape","id","digest","request","extra","receipt","archive","config","origin"})
    void damagedHeadCannotBeSignedIntoAnotherReplacement(String corruption) {
        var f=fixture.fixture.fixture;
        switch(corruption) {
            case "missing" -> f.mongo.getCollection("projects").updateOne(new Document("_id",fixture.fixture.raw().get("_id")),new Document("$unset",new Document("versions.0.reviewReplacement","")));
            case "null" -> f.change("reviewReplacement",null);
            case "shape" -> f.change("reviewReplacement","invalid");
            case "id" -> f.change("reviewReplacement.operationId",UUID.randomUUID().toString());
            case "digest" -> f.change("reviewReplacement.beforeSha256","a".repeat(64));
            case "request" -> f.change("reviewReplacement.requestId",UUID.randomUUID().toString());
            case "extra" -> f.change("reviewReplacement.extra",true);
            case "receipt" -> f.mongo.getCollection(ReviewRepairJournal.COLLECTION).updateOne(new Document("_id",first.beforeArchiveId()),new Document("$set",new Document("state","UNKNOWN")));
            case "archive" -> f.mongo.getCollection(ReviewSnapshotArchive.METADATA).updateOne(new Document("_id",first.id()),new Document("$set",new Document("actorId","other")));
            case "config" -> f.change("scanResult.remoteReview.reviewConfigSha256","e".repeat(64));
            case "origin" -> f.change("scanResult.remoteReview.origin.callerScope","e".repeat(64));
        }
        var request=fixture.request();var before=fixture.fixture.raw();
        assertThrows(RuntimeException.class,()->fixture.preparation.prepare(request,()->true));
        assertNull(fixture.fixture.archive.find(request.id()));assertEquals(before,fixture.fixture.raw());
    }

    @Test void newIsolationCanPreserveTheReplacementHeadAsWellAsOriginalJobHistory() {
        var f=fixture.fixture.fixture;f.change("scanResult.status","SCANNING");f.change("scanResult.scanState","REMOTE_REVIEW");
        fixture.fixture.isolateExpiredBrokenPoll();var result=evidence();
        assertEquals(first.id(),result.previousReplacement().operationId());assertEquals(fixture.fixture.prepared.id(),result.isolation().isolationId());
        var second=stage();assertEquals(fixture.fixture.prepared.id(),second.isolationId());
    }

    @Test void matchingTextCannotReplaceExactProjectBsonIdentity() {
        var captured=fixture.targets.capture(fixture.fixture.raw().get("_id"),0,"v");
        var version=fixture.fixture.raw().getList("versions",Document.class).getFirst();Object id=fixture.fixture.raw().get("_id");
        Object alias=id instanceof org.bson.types.ObjectId oid?oid.toHexString():new org.bson.types.ObjectId((String)id);
        assertThrows(IllegalStateException.class,()->history.verifyHead(alias,0,captured.binding(),version.get("reviewReplacement"),()->true));
        assertThrows(SecurityException.class,()->history.verifyHead(id,0,captured.binding(),version.get("reviewReplacement"),()->false));
    }
}
