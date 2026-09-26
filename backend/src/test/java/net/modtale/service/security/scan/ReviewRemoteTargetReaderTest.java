package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewRemoteTargetReaderTest {
    RemoteReviewIsolationIntegrationTest fixture=new RemoteReviewIsolationIntegrationTest();
    ReviewRemoteTargetReader reader;
    @BeforeEach void setup()throws Exception {
        fixture.setup();reader=new ReviewRemoteTargetReader(fixture.fixture.mongo);
    }
    @AfterEach void cleanup(){fixture.cleanup();}
    ReviewRemoteTargetReader.Captured capture(){return reader.capture(fixture.raw().get("_id"),0,"v");}

    @ParameterizedTest @ValueSource(strings={"running","completed","failed","isolated"})
    void capturesExactHistoryWithoutChangingSecurityStateOrContactingRemote(String state) {
        var claim=fixture.fixture.attached(30000);
        fixture.fixture.change("scanResult.verdict","BLOCK");
        switch(state) {
            case "completed" -> {fixture.fixture.change("scanResult.status","INFECTED");fixture.fixture.change("scanResult.scanState","COMPLETED");}
            case "failed" -> {fixture.fixture.change("scanResult.status","FAILED");fixture.fixture.change("scanResult.scanState","REMOTE_UNAVAILABLE");}
            case "isolated" -> fixture.isolateExpiredBrokenPoll();
        }
        var before=fixture.raw();var captured=capture();
        assertEquals(claim.binding(),captured.binding());
        assertArrayEquals(fixture.reader.capture(before.get("_id"),0,"v").versionBytes(),captured.snapshot().versionBytes());
        assertEquals(before,fixture.raw());assertEquals("BLOCK",fixture.fixture.saved().getVerdict());
        assertEquals(0,fixture.fixture.gets.get());assertEquals(0,fixture.fixture.posts.get());
        var bytes=captured.snapshot().versionBytes();bytes[0]=0;
        assertNotEquals(0,captured.snapshot().versionBytes()[0]);
    }

    @ParameterizedTest @ValueSource(strings={"floatAttempt","overflowAttempt","stringAttempt","missingOrigin","missingJob","stringManual","changedHash","changedContext"})
    void malformedOrStaleTargetsCannotBecomeReplacementEvidence(String corruption) {
        fixture.fixture.attached(30000);
        switch(corruption) {
            case "floatAttempt" -> fixture.fixture.change("scanResult.remoteReview.attempt",1.0);
            case "overflowAttempt" -> fixture.fixture.change("scanResult.remoteReview.attempt",4294967297L);
            case "stringAttempt" -> fixture.fixture.change("scanResult.remoteReview.attempt","1");
            case "missingOrigin" -> fixture.fixture.change("scanResult.remoteReview.origin",null);
            case "missingJob" -> fixture.fixture.change("scanResult.remoteReview.jobId",null);
            case "stringManual" -> fixture.fixture.change("scanResult.remoteReview.manualRescan","false");
            case "changedHash" -> fixture.fixture.change("hash","d".repeat(64));
            case "changedContext" -> fixture.fixture.change("gameVersions",java.util.List.of("changed"));
        }
        var before=fixture.raw();assertThrows(IllegalStateException.class,this::capture);
        assertEquals(before,fixture.raw());assertEquals(0,fixture.fixture.gets.get());assertEquals(0,fixture.fixture.posts.get());
    }

    @Test void stalePositionOrDuplicateVersionIdentityCannotBeCaptured() {
        fixture.fixture.attached(30000);var root=fixture.raw();
        assertThrows(IllegalStateException.class,()->reader.capture(root.get("_id"),1,"v"));
        var version=root.getList("versions",Document.class).getFirst();
        fixture.fixture.mongo.getCollection("projects").updateOne(new Document("_id",root.get("_id")),
                new Document("$push",new Document("versions",version)));
        assertThrows(IllegalStateException.class,this::capture);
    }
}
