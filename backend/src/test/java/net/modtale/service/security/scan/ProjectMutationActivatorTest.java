package net.modtale.service.security.scan;

import com.mongodb.*;
import com.mongodb.client.ClientSession;
import net.modtale.service.admin.review.*;
import net.modtale.model.project.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationActivatorTest {
    ProjectMutationAdmissionPreparationTest base=new ProjectMutationAdmissionPreparationTest();ProjectMutationAdmissionPreparation.Prepared decision;ReviewRepairJournal journal;
    @BeforeEach void setup()throws Exception{base.setup();base.base.check();decision=base.service.prepare(base.request(false),()->true);journal=new ReviewRepairJournal(f().mongo,base.base.base.base.base.archive);}
    @AfterEach void cleanup(){base.cleanup();}
    RemoteReviewStepIntegrationTest f(){return base.base.base.base.base.fixture;}
    ProjectMutationActivator activator(){return activator(f().mongo);}
    ProjectMutationActivator activator(MongoTemplate mongo){return new ProjectMutationActivator(mongo,base.base.budget,base.service,base.base.base.base.base.archive,journal);}
    Document version(){return f().mongo.getCollection("projects").find().first().getList("versions",Document.class).get(1);}
    ProjectMutationActivator.Result activate(){return activator().activate(decision,"moderator",()->true);}
    @Test void activationIsDiscoverableAndRetainedWithoutAnEphemeralEnqueue(){
        var original=version();var result=activate();assertEquals("APPLIED",result.state());assertEquals("REMOTE_REVIEW",version().get("scanResult",Document.class).get("scanState"));
        assertEquals(original.get("versionMutation"),version().get("versionMutation"));assertEquals(original.get("replacementSecurityHold"),version().get("replacementSecurityHold"));
        var candidates=new RemoteReviewDiscovery(f().mongo).page(null,64).candidates();assertEquals(1,candidates.size());assertEquals(decision.binding().requestId(),candidates.getFirst().requestId());
        assertEquals(result,activate());assertEquals(1,f().mongo.getCollection(ProjectMutationActivator.ADMISSIONS).countDocuments());assertEquals(0,f().posts.get());
        assertNotNull(new RemoteReviewPollStore(f().mongo).claim(decision.binding(),30000));
    }
    @ParameterizedTest @ValueSource(strings={"DRAFT","DELETED","ARCHIVED"})
    void withdrawnLifecycleCannotActivate(String status){
        f().mongo.getCollection("projects").updateOne(new Document("_id",base.base.project),new Document("$set",new Document("status",status)));
        assertEquals("NOT_APPLIED",activate().state());assertEquals("MUTATION_HELD",version().get("scanResult",Document.class).get("scanState"));assertEquals(0,f().mongo.getCollection(ProjectMutationActivator.ADMISSIONS).countDocuments());
    }
    @Test void heldBytesAndConfigurationChangesPreventAnExecutionClaim(){
        base.config="d".repeat(64);assertThrows(RuntimeException.class,this::activate);base.config="c".repeat(64);
        f().mongo.getCollection("projects").updateOne(new Document("_id",base.base.project),new Document("$set",new Document("versions.1.changelog","changed")));
        assertThrows(RuntimeException.class,this::activate);assertNull(f().mongo.getCollection(ReviewRepairJournal.COLLECTION).find(new Document("_id",decision.id())).first());
    }
    @Test void revocationAfterAdmissionWriteRollsBackVersionAndAdmission(){
        var mongo=spy(f().mongo);var admissions=spy(mongo.getCollection(ProjectMutationActivator.ADMISSIONS));doReturn(admissions).when(mongo).getCollection(ProjectMutationActivator.ADMISSIONS);
        var allowed=new AtomicBoolean(true);doAnswer(call->{call.callRealMethod();allowed.set(false);return null;}).when(admissions).insertOne(any(ClientSession.class),any(Document.class));
        var before=version();assertThrows(SecurityException.class,()->activator(mongo).activate(decision,"moderator",allowed::get));assertEquals(before,version());assertEquals(0,f().mongo.getCollection(ProjectMutationActivator.ADMISSIONS).countDocuments());
    }
    @Test void lostCommitReplyRecoversWithoutRepeatingActivation(){
        var mongo=spy(f().mongo);var factory=spy(mongo.getMongoDatabaseFactory());doReturn(factory).when(mongo).getMongoDatabaseFactory();
        doAnswer(call->{var session=spy((ClientSession)call.callRealMethod());doAnswer(commit->{commit.callRealMethod();throw new MongoException("lost commit reply");}).when(session).commitTransaction();return session;}).when(factory).getSession(any(ClientSessionOptions.class));
        var result=activator(mongo).activate(decision,"moderator",()->true);assertEquals("APPLIED",result.state());assertEquals(result,activate());assertEquals(1,f().mongo.getCollection(ProjectMutationActivator.ADMISSIONS).countDocuments());
    }
    @Test void collisionCannotLeaveVersionActivatedWithoutItsOwnAdmission(){
        f().mongo.getCollection(ProjectMutationActivator.ADMISSIONS).insertOne(new Document("_id",decision.binding().requestId()).append("decisionId","other"));var before=version();
        assertEquals("UNKNOWN",activate().state());assertEquals(before,version());assertEquals("UNKNOWN",activate().state());
    }
    @Test void competingDecisionsCannotActivateTheSameHeldRequestTwice(){
        var request=base.request(false);var other=base.service.prepare(new ProjectMutationAdmissionPreparation.Request(UUID.randomUUID().toString(),request.projectId(),request.versionIndex(),request.versionId(),request.heldSha256(),request.mutationId(),request.actor(),request.observations(),false),()->true);
        activate();assertThrows(RuntimeException.class,()->activator().activate(other,"moderator",()->true));assertEquals(1,f().mongo.getCollection(ProjectMutationActivator.ADMISSIONS).countDocuments());
    }
    @Test void receiptAuthenticatesAdmissionAfterVisibleProjectDeletion(){
        var result=activate();f().mongo.getCollection("projects").deleteMany(new Document());assertEquals(result,activator().receipt(decision,"moderator",()->true));
        f().mongo.getCollection(ProjectMutationActivator.ADMISSIONS).updateOne(new Document("_id",decision.binding().requestId()),new Document("$set",new Document("afterSha256","0".repeat(64))));
        assertThrows(IllegalStateException.class,()->activator().receipt(decision,"moderator",()->true));
    }
    @Test void existingWorkerDeliversAndCompletesTheFixedRequestAfterRestart(){
        assertEquals("APPLIED",activate().state());var f=f();f.server.removeContext("/api/v1/review-jobs");f.binding=decision.binding();f.job=UUID.randomUUID().toString();var created=new AtomicBoolean();
        f.route(e->{if(e.getRequestMethod().equals("POST")){created.set(true);f.reply(e,202,"QUEUED");}else f.reply(e,created.get()?200:404,"COMPLETED");});
        var bootstrap=new RemoteReviewBootstrap(new RemoteReviewPersistence(f.mongo),f.client,f.step);
        assertEquals("RECORDED",bootstrap.advance(f.project,"new-upload",f.binding.attempt(),f.binding.requestId()).state());
        f.mongo.getCollection("projects").updateOne(new Document("_id",base.base.project),new Document("$set",new Document("versions.1.scanResult.remotePoll.nextPollAt",new Date(0)).append("versions.1.scanResult.remotePoll.leaseUntil",new Date(0))));
        var restarted=new RemoteReviewBootstrap(new RemoteReviewPersistence(f.mongo),f.client,new RemoteReviewStep(new RemoteReviewPollStore(f.mongo),f.client,f.storage,f.completion));
        assertEquals("APPLIED",restarted.advance(f.project,"new-upload",f.binding.attempt(),f.binding.requestId()).state());
        var approved=f.mongo.findById(f.project,Project.class).getVersions().get(1);assertEquals(ProjectVersion.ReviewStatus.SCHEDULED,approved.getReviewStatus());assertNotNull(approved.getScheduledPublishDate());
        assertEquals(f.binding.withJobId(f.job),approved.getScanResult().getRemoteReview());assertNotNull(approved.getScanResult().getSecurityEvidence());assertEquals(f.binding.requestId(),approved.getVersionMutation().requestId());assertEquals(1,f.posts.get());
    }
}
