package net.modtale.service.security.scan;

import com.mongodb.*;
import com.mongodb.client.ClientSession;
import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewReplacementExecutorTest {
    ReviewReplacementPreparationTest fixture=new ReviewReplacementPreparationTest();
    ReviewRepairJournal journal;
    @BeforeEach void setup()throws Exception {
        fixture.setup();journal=new ReviewRepairJournal(fixture.fixture.fixture.mongo,fixture.fixture.archive);
    }
    @AfterEach void cleanup(){fixture.cleanup();}
    ReviewReplacementExecutor executor() {
        return new ReviewReplacementExecutor(fixture.fixture.fixture.mongo,fixture.preparation,fixture.fixture.reader,journal);
    }
    ReviewReplacementPreparation.Prepared prepare() {return fixture.preparation.prepare(fixture.request(),()->true);}
    Document admission(String id){return fixture.fixture.fixture.mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS).find(new Document("_id",id)).first();}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void stagesHeldReplacementAndLineageTogetherWithoutClearingPriorHolds(boolean isolated) {
        var f=fixture.fixture.fixture;f.change("scanResult.verdict","BLOCK");f.change("scanResult.holdUntilTimestamp",123456L);
        if(isolated)fixture.fixture.isolateExpiredBrokenPoll();
        var before=fixture.fixture.raw();var prepared=prepare();var result=executor().stage(prepared,"new-moderator",()->true);
        assertEquals("APPLIED",result.state());assertEquals(result,executor().stage(prepared,"new-moderator",()->true));
        assertEquals(result.afterSha256(),fixture.fixture.reader.capture(before.get("_id"),0,"v").sha256());
        assertEquals("HELD",admission(prepared.id()).get("state"));assertEquals(prepared.beforeArchiveId(),admission(prepared.id()).get("beforeArchiveId"));
        var version=f.mongo.findById(f.project,net.modtale.model.project.Project.class).getVersions().getFirst();
        assertEquals(prepared.id(),version.getReviewReplacement().operationId());assertNull(version.getReviewIsolation());
        assertEquals(prepared.replacement(),version.getScanResult().getRemoteReview());
        assertEquals("REPLACEMENT_HELD",version.getScanResult().getScanState());assertEquals("BLOCK",version.getScanResult().getVerdict());
        assertFalse(ArtifactClearancePolicy.boundToVersion(version));assertFalse(version.getScanResult().isArtifactVerified());
        assertEquals(before.getList("versions",Document.class).getFirst().get("scanResult",Document.class).get("holdUntilTimestamp"),
                fixture.fixture.raw().getList("versions",Document.class).getFirst().get("scanResult",Document.class).get("holdUntilTimestamp"));
        assertTrue(new RemoteReviewDiscovery(f.mongo).page(null,64).candidates().isEmpty());
        assertEquals("NO_WORK",f.step.advance(prepared.replacement()).state());assertEquals(0,f.gets.get());assertEquals(0,f.posts.get());
    }

    @Test void changedVersionCannotBeOverwrittenOrAdmitted() {
        var prepared=prepare();fixture.fixture.fixture.change("changelog","concurrent edit");var before=fixture.fixture.raw();
        assertEquals("NOT_APPLIED",executor().stage(prepared,"new-moderator",()->true).state());assertNull(admission(prepared.id()));
        assertEquals(before,fixture.fixture.raw());
    }
    @ParameterizedTest @ValueSource(strings={"BLOCK","INFECTED","inherited"})
    void stagingRetainsTheEarliestUnresolvedAdverseReference(String reason) {
        var f=fixture.fixture.fixture;String inherited=java.util.UUID.randomUUID().toString();
        if(reason.equals("BLOCK"))f.change("scanResult.verdict","BLOCK");
        else if(reason.equals("INFECTED"))f.change("scanResult.status","INFECTED");
        else f.change("replacementSecurityHold",inherited);
        var prepared=prepare();assertEquals("APPLIED",executor().stage(prepared,"new-moderator",()->true).state());
        var version=f.mongo.findById(f.project,net.modtale.model.project.Project.class).getVersions().getFirst();
        assertEquals(reason.equals("inherited")?inherited:prepared.id(),version.getReplacementSecurityHold());
        assertEquals("BLOCK",version.getScanResult().getVerdict());
    }

    @Test void admissionInsertFailureRollsBackVersionAndCannotBeReexecuted() {
        var prepared=prepare();var before=fixture.fixture.raw();
        fixture.fixture.fixture.mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS).insertOne(new Document("_id",prepared.id()).append("state","collision"));
        assertEquals("UNKNOWN",executor().stage(prepared,"new-moderator",()->true).state());assertEquals(before,fixture.fixture.raw());
        fixture.fixture.fixture.mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS).deleteMany(new Document());
        assertEquals("UNKNOWN",executor().stage(prepared,"new-moderator",()->true).state());assertNull(admission(prepared.id()));assertEquals(before,fixture.fixture.raw());
    }

    @Test void permissionRevocationAfterVersionWriteRollsBackAdmissionAndVersion() {
        var prepared=prepare();var before=fixture.fixture.raw();var allowed=new AtomicBoolean(true);var reader=spy(fixture.fixture.reader);
        doAnswer(call->{var captured=(RawReviewSnapshotReader.Captured)call.callRealMethod();
            if(!prepared.beforeSha256().equals(captured.sha256()))allowed.set(false);return captured;
        }).when(reader).capture(any(ClientSession.class),eq(before.get("_id")),eq(0),eq("v"));
        var executor=new ReviewReplacementExecutor(fixture.fixture.fixture.mongo,fixture.preparation,reader,journal);
        assertThrows(SecurityException.class,()->executor.stage(prepared,"new-moderator",allowed::get));
        assertEquals(before,fixture.fixture.raw());assertNull(admission(prepared.id()));
    }

    @Test void lostCommitReplyRecoversOneAppliedVersionAndAdmission() {
        var prepared=prepare();var mongo=spy(fixture.fixture.fixture.mongo);var factory=spy(mongo.getMongoDatabaseFactory());
        doReturn(factory).when(mongo).getMongoDatabaseFactory();
        doAnswer(call->{var session=spy((ClientSession)call.callRealMethod());
            doAnswer(commit->{commit.callRealMethod();throw new MongoException("lost commit reply");}).when(session).commitTransaction();return session;
        }).when(factory).getSession(any(ClientSessionOptions.class));
        var result=new ReviewReplacementExecutor(mongo,fixture.preparation,fixture.fixture.reader,journal).stage(prepared,"new-moderator",()->true);
        assertEquals("APPLIED",result.state());assertEquals(result,executor().stage(prepared,"new-moderator",()->true));
        assertEquals(1,fixture.fixture.fixture.mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS).countDocuments());
    }

    @Test void originalJobCannotWriteIntoStagedReplacement() {
        var f=fixture.fixture.fixture;
        // The fixture has an attached claim already; reconstruct the same current claim from its persisted token.
        var old=f.saved().getRemoteReview();var poll=f.saved().getRemotePoll();
        var claim=new RemoteReviewPollStore.Claim(old,poll.token());
        var prepared=prepare();assertEquals("APPLIED",executor().stage(prepared,"new-moderator",()->true).state());var before=fixture.fixture.raw();
        assertFalse(f.completion.handleRemoteCompletedScan(claim,f.cleanResult()));assertFalse(f.polls.release(claim,100));
        assertEquals(before,fixture.fixture.raw());
    }

    @Test void concurrentBsonTypeChangeAfterCaptureAbortsTheTransaction() {
        var prepared=prepare();var reader=spy(fixture.fixture.reader);var changed=new AtomicBoolean();
        Object id=fixture.fixture.raw().get("_id");
        doAnswer(call->{var captured=(RawReviewSnapshotReader.Captured)call.callRealMethod();
            if(changed.compareAndSet(false,true))fixture.fixture.fixture.change("scanResult.scanAttempt",1L);return captured;
        }).when(reader).capture(any(ClientSession.class),eq(id),eq(0),eq("v"));
        var result=new ReviewReplacementExecutor(fixture.fixture.fixture.mongo,fixture.preparation,reader,journal).stage(prepared,"new-moderator",()->true);
        assertEquals("UNKNOWN",result.state());assertNull(admission(prepared.id()));
        var scan=fixture.fixture.raw().getList("versions",Document.class).getFirst().get("scanResult",Document.class);
        assertInstanceOf(Long.class,scan.get("scanAttempt"));assertEquals(fixture.fixture.fixture.binding.requestId(),scan.get("scanRequestId"));
    }
}
