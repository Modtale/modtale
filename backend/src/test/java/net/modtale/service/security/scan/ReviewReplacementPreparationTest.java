package net.modtale.service.security.scan;

import net.modtale.model.project.RemoteReviewOrigin;
import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewReplacementPreparationTest {
    RemoteReviewIsolationIntegrationTest fixture=new RemoteReviewIsolationIntegrationTest();
    ReviewReplacementEvidenceReader evidence;
    ReviewRemoteTargetReader targets;
    Clock clock=Clock.fixed(Instant.now(),ZoneOffset.UTC);
    ReviewReplacementPreparation preparation;
    ReviewReplacementPreparation.Configuration configuration=new ReviewReplacementPreparation.Configuration("warden-3.0.0:"+"a".repeat(64),
            "d".repeat(64),new RemoteReviewOrigin("22222222-2222-2222-2222-222222222222","f".repeat(64)));
    @BeforeEach void setup()throws Exception {
        fixture.setup();fixture.fixture.attached(30000);targets=new ReviewRemoteTargetReader(fixture.fixture.mongo);
        evidence=new ReviewReplacementEvidenceReader(targets,new ReviewOrphanTargetResolver(fixture.fixture.mongo,fixture.archive,fixture.isolation));
        preparation=create(fixture.archive,clock);
    }
    ReviewReplacementPreparation create(ReviewSnapshotArchive archive,Clock clock) {
        return new ReviewReplacementPreparation(evidence,targets,fixture.reader,archive,clock,60000);
    }
    @AfterEach void cleanup(){fixture.cleanup();}
    ReviewReplacementPreparation.Request request() {
        var snapshot=fixture.reader.capture(fixture.raw().get("_id"),0,"v");
        return new ReviewReplacementPreparation.Request(UUID.randomUUID().toString(),snapshot.projectId(),0,"v",snapshot.sha256(),"new-moderator",configuration);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void retainsExactBeforeEvidenceAndStableDistinctNewConfiguration(boolean isolated) {
        fixture.fixture.change("scanResult.verdict","BLOCK");if(isolated)fixture.isolateExpiredBrokenPoll();
        var request=request();var before=fixture.raw();var expected=fixture.reader.capture(request.projectId(),0,"v").versionBytes();
        var prepared=preparation.prepare(request,()->true);
        assertEquals(prepared,create(fixture.archive,Clock.offset(clock,Duration.ofSeconds(10))).prepare(request,()->true));
        assertEquals(prepared,create(fixture.archive,clock).recover(request.id(),request.actorId(),()->true));
        var retained=fixture.archive.load(prepared.beforeArchiveId());assertArrayEquals(expected,retained.versionBytes());
        assertEquals("new-moderator",retained.actorId());assertEquals(request.expectedSha256(),prepared.beforeSha256());
        assertEquals(configuration.origin(),prepared.replacement().origin());assertEquals(configuration.policyVersion(),prepared.replacement().policyVersion());
        assertEquals(configuration.reviewConfigSha256(),prepared.replacement().reviewConfigSha256());
        assertNotEquals(fixture.fixture.binding.origin(),prepared.replacement().origin());
        assertNotEquals(fixture.fixture.binding.requestId(),prepared.replacement().requestId());assertEquals(2,prepared.replacement().attempt());
        assertNull(prepared.replacement().jobId());assertTrue(prepared.replacement().manualRescan());
        assertEquals(isolated?fixture.prepared.id():null,prepared.isolationId());
        assertEquals(isolated?fixture.prepared.sha256():null,prepared.isolationBeforeSha256());
        assertEquals(before,fixture.raw());assertEquals(0,fixture.fixture.gets.get());assertEquals(0,fixture.fixture.posts.get());
    }

    @ParameterizedTest @ValueSource(strings={"actor","config","changedVersion","expired"})
    void retryCannotChangeAnIntentOrExtendItsLifetime(String change) {
        var request=request();var prepared=preparation.prepare(request,()->true);
        var service=preparation;var retry=request;
        switch(change) {
            case "actor" -> retry=new ReviewReplacementPreparation.Request(request.id(),request.projectId(),0,"v",request.expectedSha256(),"other",configuration);
            case "config" -> retry=new ReviewReplacementPreparation.Request(request.id(),request.projectId(),0,"v",request.expectedSha256(),request.actorId(),
                    new ReviewReplacementPreparation.Configuration(configuration.policyVersion(),"e".repeat(64),configuration.origin()));
            case "changedVersion" -> fixture.fixture.change("changelog","new content");
            case "expired" -> service=create(fixture.archive,Clock.offset(clock,Duration.ofSeconds(60)));
        }
        var fixedService=service;var fixedRetry=retry;
        assertThrows(RuntimeException.class,()->fixedService.prepare(fixedRetry,()->true));
        assertEquals(prepared,service.recover(request.id(),request.actorId(),()->true));
    }

    @Test void recoveryRemainsLocalAfterDeletionButCannotBeUsedByAnotherActor() {
        var request=request();var prepared=preparation.prepare(request,()->true);
        fixture.fixture.mongo.getCollection("projects").deleteMany(new Document());
        assertEquals(prepared,preparation.recover(request.id(),request.actorId(),()->true));
        assertThrows(IllegalStateException.class,()->preparation.recover(request.id(),"other",()->true));
        assertEquals(0,fixture.fixture.gets.get());assertEquals(0,fixture.fixture.posts.get());
    }

    @ParameterizedTest @ValueSource(strings={"before","intent"})
    void tamperingEitherRetainedRecordPreventsRecovery(String target) {
        var request=request();var prepared=preparation.prepare(request,()->true);
        String id=target.equals("before")?prepared.beforeArchiveId():prepared.id();
        fixture.fixture.mongo.getCollection(ReviewSnapshotArchive.METADATA).updateOne(new Document("_id",id),new Document("$set",new Document("expiresAt",clock.millis()+120000)));
        assertThrows(IllegalStateException.class,()->preparation.recover(request.id(),request.actorId(),()->true));
    }

    @ParameterizedTest @ValueSource(strings={"before","intent"})
    void lostRetentionReplyCannotAllocateAnotherRequestOrRefreshExpiry(String stage) {
        var archive=spy(fixture.archive);var fail=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call->{var saved=(ReviewSnapshotArchive.Snapshot)call.callRealMethod();
            boolean selected=(saved.action()==ReviewSnapshotArchive.Action.REPLACEMENT_INTENT)==stage.equals("intent");
            if(selected && fail.getAndSet(false))throw new IllegalStateException("lost reply");return saved;
        }).when(archive).retain(any());
        var request=request();var service=create(archive,clock);
        if(stage.equals("intent"))assertThrows(IllegalStateException.class,()->service.prepare(request,()->true));
        else service.prepare(request,()->true);
        var recovered=preparation.recover(request.id(),request.actorId(),()->true);
        assertEquals(recovered,create(fixture.archive,Clock.offset(clock,Duration.ofSeconds(5))).prepare(request,()->true));
        assertEquals(clock.millis()+60000,recovered.expiresAt());
    }

    @Test void changedCurrentVersionDuringRetentionLeavesOnlyRecoverableProposal() {
        var archive=spy(fixture.archive);
        doAnswer(call->{var saved=(ReviewSnapshotArchive.Snapshot)call.callRealMethod();
            if(saved.action()==ReviewSnapshotArchive.Action.REPLACEMENT_INTENT)fixture.fixture.change("changelog","concurrent edit");return saved;
        }).when(archive).retain(any());
        var request=request();assertThrows(IllegalStateException.class,()->create(archive,clock).prepare(request,()->true));
        assertNotNull(preparation.recover(request.id(),request.actorId(),()->true));
        assertEquals("concurrent edit",fixture.raw().getList("versions",Document.class).getFirst().get("changelog"));
        assertEquals(fixture.fixture.job,fixture.fixture.saved().getRemoteReview().jobId());
    }

    @Test void deniedAccessCannotRetainOrRecoverEvidence() {
        var archive=spy(fixture.archive);var request=request();var service=create(archive,clock);
        assertThrows(SecurityException.class,()->service.prepare(request,()->false));
        assertThrows(SecurityException.class,()->service.recover(request.id(),request.actorId(),()->false));
        verifyNoInteractions(archive);
    }

    @Test void attemptExhaustionCannotWrapOrRetainAProposal() {
        fixture.fixture.change("scanResult.scanAttempt",Integer.MAX_VALUE);
        fixture.fixture.change("scanResult.remoteReview.attempt",Integer.MAX_VALUE);
        var archive=spy(fixture.archive);var request=request();
        assertThrows(ArithmeticException.class,()->create(archive,clock).prepare(request,()->true));
        verifyNoInteractions(archive);
    }

    @Test void revocationDuringRetentionDoesNotReturnPreparationAuthority() {
        var permitted=new java.util.concurrent.atomic.AtomicBoolean(true);var archive=spy(fixture.archive);
        doAnswer(call->{var saved=(ReviewSnapshotArchive.Snapshot)call.callRealMethod();permitted.set(false);return saved;}).when(archive).retain(any());
        var request=request();var before=fixture.raw();
        assertThrows(SecurityException.class,()->create(archive,clock).prepare(request,permitted::get));
        assertEquals(before,fixture.raw());assertNull(fixture.archive.find(request.id()));
        assertEquals(0,fixture.fixture.gets.get());assertEquals(0,fixture.fixture.posts.get());
    }
}
