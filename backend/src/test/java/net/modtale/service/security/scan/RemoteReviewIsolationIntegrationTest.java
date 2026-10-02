package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import net.modtale.model.project.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class RemoteReviewIsolationIntegrationTest {
    RemoteReviewStepIntegrationTest fixture=new RemoteReviewStepIntegrationTest();
    ReviewSnapshotArchive archive;RawReviewSnapshotReader reader;ReviewIsolationExecutor isolation;
    ReviewRepairPreparation.Prepared prepared;
    @BeforeEach void setup()throws Exception {
        fixture.setup(System.getenv().getOrDefault("WARDEN_REPAIR_TX_DB_PORT","27031"),true);
        reader=new RawReviewSnapshotReader(fixture.mongo);archive=new ReviewSnapshotArchive(fixture.mongo,"test",Map.of("test",new byte[32]));
        isolation=new ReviewIsolationExecutor(fixture.mongo,archive,reader,new ReviewRepairJournal(fixture.mongo,archive));
    }
    @AfterEach void cleanup(){fixture.cleanup();}
    Document raw(){return fixture.mongo.getCollection("projects").find().first();}
    void isolateExpiredBrokenPoll() {
        fixture.change("scanResult.remotePoll.leaseUntil",new Date(0));
        fixture.change("scanResult.remotePoll.extra","invalid poll shape");
        Object rootId=raw().get("_id");var capture=reader.capture(rootId,0,"v");
        prepared=new ReviewRepairPreparation(archive,reader,Clock.systemUTC(),1,60000).prepare(
                new ReviewRepairPreparation.Request(UUID.randomUUID().toString(),rootId,0,"v",capture.sha256(),"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW),()->true);
        assertEquals("APPLIED",isolation.execute(prepared,"actor",()->true).state());
        assertArrayEquals(capture.versionBytes(),archive.load(prepared.id()).versionBytes());
    }
    void assertIsolated() {
        var version=fixture.mongo.findById(fixture.project,Project.class).getVersions().getFirst();
        assertEquals(ProjectVersion.ReviewStatus.PENDING,version.getReviewStatus());assertEquals("REMOTE_ISOLATED",version.getScanResult().getScanState());
        assertEquals(ScanStatus.FAILED,version.getScanResult().getStatus());assertFalse(version.getScanResult().isArtifactVerified());
        assertNull(version.getScheduledPublishDate());assertEquals(0,version.getSecurityApprovedAt());
        assertEquals(fixture.job,version.getScanResult().getRemoteReview().jobId());
        assertEquals(prepared.id(),version.getReviewIsolation().operationId());
    }
    @Test void isolatedVersionRejectsStaleCompletionAndEveryPollMutation() {
        var claim=fixture.attached(30000);fixture.change("scanResult.verdict","BLOCK");isolateExpiredBrokenPoll();var before=raw();
        assertFalse(fixture.completion.handleRemoteCompletedScan(claim,fixture.cleanResult()));
        assertFalse(fixture.polls.isCurrent(claim));assertFalse(fixture.polls.release(claim,100));assertNull(fixture.polls.claim(claim.binding(),1000));
        assertNull(fixture.polls.attachJob(claim,fixture.job));
        var status=new RemoteReviewClient.Status(fixture.job,"QUEUED",true,1000,2000,null);
        assertFalse(fixture.polls.recordStatusAndRelease(claim,status,100));
        assertFalse(fixture.polls.finishUnavailable(claim,new RemoteReviewClient.Status(fixture.job,"CANCELLED",true,1000,2000,null)));
        assertEquals("NO_WORK",fixture.step.advance(claim.binding()).state());assertTrue(new RemoteReviewDiscovery(fixture.mongo).page(null,64).candidates().isEmpty());
        assertEquals(before,raw());assertIsolated();assertEquals("BLOCK",fixture.saved().getVerdict());assertEquals(0,fixture.posts.get());assertEquals(0,fixture.gets.get());
        verifyNoInteractions(fixture.storage);
    }
    @Test void cleanHttpResultArrivingAfterIsolationCannotRestoreClearance() {
        var reached=new AtomicBoolean();
        fixture.route(e->{if(e.getRequestURI().getPath().endsWith("/result")){isolateExpiredBrokenPoll();reached.set(true);}fixture.reply(e,200,"COMPLETED");});
        assertEquals("SUPERSEDED",fixture.step.advance(fixture.binding).state());assertTrue(reached.get());assertIsolated();
        assertEquals(0,fixture.posts.get());assertEquals(2,fixture.gets.get());verifyNoInteractions(fixture.storage);
    }
    @Test void isolationAfterCompletionSnapshotRejectsFinalPublicationWrite() {
        var claim=fixture.attached(30000);var reached=new AtomicBoolean();
        when(fixture.policy.currentPolicyVersion()).thenAnswer(i->{isolateExpiredBrokenPoll();reached.set(true);return fixture.binding.policyVersion();});
        assertFalse(fixture.completion.handleRemoteCompletedScan(claim,fixture.cleanResult()));assertTrue(reached.get());assertIsolated();
        assertEquals("APPLIED",isolation.recover(prepared.id(),"actor").receipt().outcome().state());
    }
    @Test void manualRescanCannotEraseAnUnaccountedRemoteJobOrItsIsolatedReference() {
        fixture.attached(30000);fixture.change("scanResult.verdict","BLOCK");
        for(boolean isolated:List.of(false,true)) {
            if(isolated)isolateExpiredBrokenPoll();
            var before=raw();var version=fixture.mongo.findById(fixture.project,Project.class).getVersions().getFirst();
            var writes=new VersionReviewPersistence(fixture.mongo);var snapshot=writes.captureForRescan(fixture.project,"v",VersionReviewSnapshot.rescanToken(version));
            var queued=new ScanResult();queued.setStatus(ScanStatus.SCANNING);queued.setScanState("QUEUED");queued.setScanAttempt(2);
            var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->writes.queueRescan(snapshot,queued));
            assertEquals(409,error.getStatusCode().value());assertEquals(before,raw());assertEquals("BLOCK",fixture.saved().getVerdict());
        }
        assertEquals(0,fixture.posts.get());assertEquals(0,fixture.gets.get());
    }
}
