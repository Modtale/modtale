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
class ReviewReplacementActivatorTest {
    ReviewReplacementActivationDecisionTest base=new ReviewReplacementActivationDecisionTest();
    ReviewReplacementActivationDecision.Prepared decision;ReviewRepairJournal journal;
    @BeforeEach void setup()throws Exception {
        base.setup();base.observe(200,"COMPLETED","COMPLETED");decision=base.prepare(false);
        journal=new ReviewRepairJournal(base.base.fixture.fixture.fixture.mongo,base.base.fixture.fixture.archive);
    }
    @AfterEach void cleanup(){base.cleanup();}
    ReviewReplacementActivator activator() {
        var f=base.base.fixture;
        return new ReviewReplacementActivator(f.fixture.fixture.mongo,base.decisions,f.fixture.archive,journal,f.fixture.reader,()->f.configuration);
    }
    Document admission(){return base.base.fixture.fixture.fixture.mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS).find().first();}

    @Test void activationBecomesDurablyDiscoverableWithoutAnInMemoryEnqueue() {
        var f=base.base.fixture.fixture.fixture;var result=activator().activate(decision,"next-moderator",()->true);
        assertEquals("APPLIED",result.state());assertEquals("ACTIVE",admission().get("state"));assertEquals(decision.id(),admission().get("activationId"));
        assertEquals(base.base.prepared.replacement(),f.saved().getRemoteReview());assertEquals("REMOTE_REVIEW",f.saved().getScanState());
        var discovered=new RemoteReviewDiscovery(f.mongo).page(null,64).candidates();assertEquals(1,discovered.size());
        assertEquals(base.base.prepared.replacement().requestId(),discovered.getFirst().requestId());
        assertEquals(result,activator().activate(decision,"next-moderator",()->true));assertEquals(1,f.gets.get());assertEquals(0,f.posts.get());
        assertNotNull(new RemoteReviewPollStore(f.mongo).claim(base.base.prepared.replacement(),30000));
    }

    @ParameterizedTest @ValueSource(strings={"DRAFT","ARCHIVED","DELETED"})
    void withdrawnLifecycleCannotActivate(String status) {
        var f=base.base.fixture.fixture.fixture;f.mongo.getCollection("projects").updateOne(new Document("_id",base.base.fixture.fixture.raw().get("_id")),new Document("$set",new Document("status",status)));
        assertEquals("NOT_APPLIED",activator().activate(decision,"next-moderator",()->true).state());assertEquals("HELD",admission().get("state"));
        assertTrue(new RemoteReviewDiscovery(f.mongo).page(null,64).candidates().isEmpty());
    }

    @Test void changedConfigurationCannotActivateOrReserveAnExecution() {
        var f=base.base.fixture;
        var changed=new ReviewReplacementActivator(f.fixture.fixture.mongo,base.decisions,f.fixture.archive,journal,f.fixture.reader,()->null);
        assertThrows(IllegalStateException.class,()->changed.activate(decision,"next-moderator",()->true));assertEquals("HELD",admission().get("state"));
        assertNull(f.fixture.fixture.mongo.getCollection(ReviewRepairJournal.COLLECTION).find(new Document("_id",decision.id())).first());
    }

    @Test void revocationAfterVersionWriteRollsBackActivation() {
        var f=base.base.fixture;var reader=spy(f.fixture.reader);var allowed=new AtomicBoolean(true);var before=f.fixture.raw();
        doAnswer(call->{var captured=(RawReviewSnapshotReader.Captured)call.callRealMethod();if(!decision.heldSha256().equals(captured.sha256()))allowed.set(false);return captured;})
                .when(reader).capture(any(ClientSession.class),eq(before.get("_id")),eq(0),eq("v"));
        var activator=new ReviewReplacementActivator(f.fixture.fixture.mongo,base.decisions,f.fixture.archive,journal,reader,()->f.configuration);
        assertThrows(SecurityException.class,()->activator.activate(decision,"next-moderator",allowed::get));
        assertEquals(before,f.fixture.raw());assertEquals("HELD",admission().get("state"));
    }

    @Test void configurationChangeAfterVersionWriteRollsBackActivation() {
        var f=base.base.fixture;var reader=spy(f.fixture.reader);var unchanged=new AtomicBoolean(true);var before=f.fixture.raw();
        doAnswer(call->{var captured=(RawReviewSnapshotReader.Captured)call.callRealMethod();if(!decision.heldSha256().equals(captured.sha256()))unchanged.set(false);return captured;})
                .when(reader).capture(any(ClientSession.class),eq(before.get("_id")),eq(0),eq("v"));
        var activator=new ReviewReplacementActivator(f.fixture.fixture.mongo,base.decisions,f.fixture.archive,journal,reader,()->unchanged.get()?f.configuration:null);
        assertEquals("UNKNOWN",activator.activate(decision,"next-moderator",()->true).state());
        assertEquals(before,f.fixture.raw());assertEquals("HELD",admission().get("state"));
        assertTrue(new RemoteReviewDiscovery(f.fixture.fixture.mongo).page(null,64).candidates().isEmpty());
    }

    @Test void changedHeldEvidenceCannotConsumeTheSignedDecision() {
        var f=base.base.fixture.fixture.fixture;f.change("replacementSecurityHold",java.util.UUID.randomUUID().toString());
        assertThrows(IllegalStateException.class,()->activator().activate(decision,"next-moderator",()->true));
        assertEquals("HELD",admission().get("state"));assertEquals("REPLACEMENT_HELD",f.saved().getScanState());
        assertNull(f.mongo.getCollection(ReviewRepairJournal.COLLECTION).find(new Document("_id",decision.id())).first());
    }

    @Test void lostCommitReplyRecoversWithoutRepeatingActivation() {
        var f=base.base.fixture;var mongo=spy(f.fixture.fixture.mongo);var factory=spy(mongo.getMongoDatabaseFactory());doReturn(factory).when(mongo).getMongoDatabaseFactory();
        doAnswer(call->{var session=spy((ClientSession)call.callRealMethod());doAnswer(commit->{commit.callRealMethod();throw new MongoException("lost commit reply");}).when(session).commitTransaction();return session;})
                .when(factory).getSession(any(ClientSessionOptions.class));
        var result=new ReviewReplacementActivator(mongo,base.decisions,f.fixture.archive,journal,f.fixture.reader,()->f.configuration).activate(decision,"next-moderator",()->true);
        assertEquals("APPLIED",result.state());assertEquals(result,activator().activate(decision,"next-moderator",()->true));
        assertEquals(1,f.fixture.fixture.mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS).countDocuments());
    }

    @Test void activatedRequestIsDeliveredOnceAndCompletedByTheExistingWorker() {
        var f=base.base.fixture.fixture.fixture;
        assertEquals("APPLIED",activator().activate(decision,"next-moderator",()->true).state());
        f.server.removeContext("/api/v1/review-jobs");f.binding=base.base.prepared.replacement();f.origin=f.binding.origin();f.job=java.util.UUID.randomUUID().toString();
        when(f.policy.currentPolicyVersion()).thenReturn(f.binding.policyVersion());var created=new AtomicBoolean();
        f.route(exchange->{if(exchange.getRequestMethod().equals("POST")){created.set(true);f.reply(exchange,202,"QUEUED");}
            else f.reply(exchange,created.get()?200:404,"COMPLETED");});
        assertEquals("RECORDED",f.step.advance(f.binding).state());assertEquals(1,f.posts.get());
        f.ready();var restarted=new RemoteReviewStep(new RemoteReviewPollStore(f.mongo),f.client,f.storage,f.completion);
        assertEquals("APPLIED",restarted.advance(f.saved().getRemoteReview()).state());
        var approved=f.mongo.findById(f.project,net.modtale.model.project.Project.class).getVersions().getFirst();
        assertEquals(net.modtale.model.project.ProjectVersion.ReviewStatus.APPROVED,approved.getReviewStatus());
        assertNotNull(approved.getApprovedSecurityEvidence());assertEquals(f.binding.artifactSha256(),approved.getApprovedSecurityEvidence().artifactSha256());
        assertEquals(f.binding.requestId(),approved.getReviewReplacement().requestId());assertEquals(1,f.posts.get());
        verify(f.storage,times(1)).downloadBounded(anyString(),anyInt());
    }
}
