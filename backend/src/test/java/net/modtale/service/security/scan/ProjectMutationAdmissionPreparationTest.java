package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationAdmissionPreparationTest {
    ProjectMutationJobAccountingTest base=new ProjectMutationJobAccountingTest();ProjectMutationPriorWorkReader prior;ProjectMutationAdmissionPreparation service;
    String id=UUID.randomUUID().toString(),config="c".repeat(64),state="COMPLETED",workState="COMPLETED";int status=200;
    Clock clock=Clock.fixed(Instant.now(),ZoneOffset.UTC);
    @BeforeEach void setup()throws Exception {
        base.setup();var f=base.base.base.base.fixture;
        var old=new RawBsonDocument(base.base.base.base.archive.load(base.prepared.beforeArchiveId()).versionBytes()).decode(new DocumentCodec());
        f.mongo.getCollection("projects").replaceOne(new Document("_id",base.project),old);
        var request=base.base.base.request();var next=new RawBsonDocument(request.proposedProject()).decode(new DocumentCodec());
        var upload=next.getList("versions",Document.class).get(1);upload.put("fileUrl",f.binding.filePath());upload.put("hash",f.binding.artifactSha256());
        base.prepared=base.base.base.service.prepare(new ProjectMutationPreparation.Request(request.id(),request.projectId(),request.expectedSha256(),request.actor(),request.mutation(),VersionMutationPreparationTest.bytes(next)),()->true);
        assertEquals("APPLIED",base.base.executor().apply(base.prepared,"owner",()->true).state());
        prior=new ProjectMutationPriorWorkReader(f.mongo,base.budget,base.history);service=create(base.base.base.base.archive,clock);
        f.route(e->{if(e.getRequestURI().getPath().endsWith("/configuration")){
            byte[] bytes=f.mapper.writeValueAsBytes(Map.of("policyVersion",f.binding.policyVersion(),"reviewConfigSha256",config));e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);
        }else f.reply(e,status,state,workState);});
    }
    ProjectMutationAdmissionPreparation create(ReviewSnapshotArchive archive,Clock time){var f=base.base.base.base.fixture;return new ProjectMutationAdmissionPreparation(f.mongo,base.budget,archive,base.history,prior,base.accounting,f.client,time,60000);}
    @AfterEach void cleanup(){base.cleanup();}
    ProjectMutationAdmissionPreparation.Request request(boolean ack){
        var capture=new RawReviewSnapshotReader(base.base.base.base.fixture.mongo).capture(base.project,1,"new-upload");
        return new ProjectMutationAdmissionPreparation.Request(id,base.project,1,"new-upload",capture.sha256(),base.prepared.id(),"moderator",Map.of(base.prepared.id()+"/v",base.id),ack);
    }
    @Test void signedDecisionBindsFixedHeldIdentityWithoutActivatingIt(){
        base.check();var before=base.base.root();var request=request(false);var decision=service.prepare(request,()->true);
        assertEquals("PRIOR_WORK_ACCOUNTED",decision.rule());assertNull(decision.binding().jobId());assertEquals(base.base.base.base.fixture.origin,decision.binding().origin());
        assertEquals(before.getList("versions",Document.class).get(1).get("scanResult",Document.class).get("scanRequestId"),decision.binding().requestId());
        assertEquals(decision,service.prepare(request,()->true));assertEquals(decision,service.recover(id,"moderator",()->true));
        assertEquals(before,base.base.root());assertEquals(0,base.base.base.base.fixture.posts.get());
    }
    @ParameterizedTest @ValueSource(strings={"RUNNING","QUEUED","CANCELLED","EXPIRED","HELD"})
    void nonCompletedOriginalRequiresExplicitDuplicateWorkAcknowledgment(String original){
        state=original;workState=null;base.check();assertThrows(IllegalStateException.class,()->service.prepare(request(false),()->true));
        assertEquals("ACKNOWLEDGED_UNCERTAINTY",service.prepare(request(true),()->true).rule());
    }
    @Test void completedJobWithUnfinishedWorkerStillRequiresAcknowledgment(){workState="RUNNING";base.check();assertThrows(IllegalStateException.class,()->service.prepare(request(false),()->true));assertEquals("ACKNOWLEDGED_UNCERTAINTY",service.prepare(request(true),()->true).rule());}
    @Test void missingObservationCannotBeAcknowledgedAway(){assertThrows(RuntimeException.class,()->service.prepare(request(true),()->true));}
    @Test void activeObservationCannotBeAcknowledgedAway(){
        base.check();base.base.base.base.fixture.mongo.getCollection(ProjectMutationJobAccounting.COLLECTION).updateOne(new Document("_id",base.id),new Document("$set",new Document("state","READING")).append("$unset",new Document("observation",1).append("receivedAt",1)));
        assertThrows(RuntimeException.class,()->service.prepare(request(true),()->true));
    }
    @Test void extraObservationAndDifferentActorCannotReuseDecision(){
        base.check();var request=request(false);service.prepare(request,()->true);assertThrows(IllegalStateException.class,()->service.recover(id,"other",()->true));
        var extra=new HashMap<>(request.observations());extra.put("other",UUID.randomUUID().toString());
        assertThrows(IllegalStateException.class,()->service.prepare(new ProjectMutationAdmissionPreparation.Request(id,base.project,1,"new-upload",request.heldSha256(),base.prepared.id(),"moderator",extra,false),()->true));
    }
    @Test void configurationAndHeldChangesCannotReuseDecision(){
        base.check();var request=request(false);service.prepare(request,()->true);config="d".repeat(64);assertThrows(RuntimeException.class,()->service.prepare(request,()->true));
        config="c".repeat(64);base.base.base.base.fixture.mongo.getCollection("projects").updateOne(new Document("_id",base.project),new Document("$set",new Document("versions.1.hash","0".repeat(64))));
        assertThrows(RuntimeException.class,()->service.prepare(request,()->true));
    }
    @Test void expiryAllowsHistoryReadButNeverExtendsAdmission(){
        base.check();var request=request(false);var decision=service.prepare(request,()->true);var later=create(base.base.base.base.archive,Clock.offset(clock,Duration.ofMinutes(2)));
        assertThrows(IllegalStateException.class,()->later.prepare(request,()->true));base.base.base.base.fixture.mongo.getCollection("projects").deleteMany(new Document());
        assertEquals(decision,later.recover(id,"moderator",()->true));
    }
    @Test void lostRetentionReplyRecoversOnlyExactSignedDecision(){
        base.check();var archive=spy(base.base.base.base.archive);var once=new AtomicBoolean();doAnswer(call->{var result=call.callRealMethod();if(!once.getAndSet(true))throw new IllegalStateException("reply lost");return result;}).when(archive).retain(any());
        var result=create(archive,clock).prepare(request(false),()->true);assertTrue(once.get());assertEquals(result,service.recover(id,"moderator",()->true));
    }
    @Test void purelyNewUploadNeedsNoObservationOfUnchangedOlderVersions(){
        var f=base.base.base.base.fixture;var old=new RawBsonDocument(base.base.base.base.archive.load(base.prepared.beforeArchiveId()).versionBytes()).decode(new DocumentCodec());
        f.mongo.getCollection("projects").replaceOne(new Document("_id",base.project),old);var captured=base.base.base.service.capture(base.project,()->true);
        var versions=new ArrayList<>(old.getList("versions",Document.class));versions.add(new Document("_id","fresh").append("fileUrl",f.binding.filePath()).append("hash",f.binding.artifactSha256()).append("reviewStatus","PENDING"));old.put("versions",versions);
        var proposal=new ProjectMutationPreparation.Request(UUID.randomUUID().toString(),base.project,captured.sha256(),"owner",ProjectMutationPreparation.Mutation.VERSION_LIST,VersionMutationPreparationTest.bytes(old));
        var prepared=base.base.base.service.prepare(proposal,()->true);assertEquals("APPLIED",base.base.executor().apply(prepared,"owner",()->true).state());
        var held=new RawReviewSnapshotReader(f.mongo).capture(base.project,2,"fresh");
        var request=new ProjectMutationAdmissionPreparation.Request(id,base.project,2,"fresh",held.sha256(),prepared.id(),"moderator",Map.of(),false);
        assertEquals("PRIOR_WORK_ACCOUNTED",service.prepare(request,()->true).rule());assertEquals(1,f.gets.get());assertEquals(0,f.posts.get());
    }
    @Test void unresolvedOriginalRequiresAcknowledgmentWithoutInventingAnObservation(){
        var f=base.base.base.base.fixture;var old=new RawBsonDocument(base.base.base.base.archive.load(base.prepared.beforeArchiveId()).versionBytes()).decode(new DocumentCodec());
        old.getList("versions",Document.class).getFirst().remove("scanResult");f.mongo.getCollection("projects").replaceOne(new Document("_id",base.project),old);
        var proposal=base.base.base.request();var next=new RawBsonDocument(proposal.proposedProject()).decode(new DocumentCodec());
        next.getList("versions",Document.class).get(1).putAll(new Document("fileUrl",f.binding.filePath()).append("hash",f.binding.artifactSha256()));
        base.prepared=base.base.base.service.prepare(new ProjectMutationPreparation.Request(proposal.id(),base.project,proposal.expectedSha256(),"owner",proposal.mutation(),VersionMutationPreparationTest.bytes(next)),()->true);
        assertEquals("APPLIED",base.base.executor().apply(base.prepared,"owner",()->true).state());var held=new RawReviewSnapshotReader(f.mongo).capture(base.project,1,"new-upload");
        var request=new ProjectMutationAdmissionPreparation.Request(id,base.project,1,"new-upload",held.sha256(),base.prepared.id(),"moderator",Map.of(),false);
        assertThrows(IllegalStateException.class,()->service.prepare(request,()->true));
        assertEquals("ACKNOWLEDGED_UNCERTAINTY",service.prepare(new ProjectMutationAdmissionPreparation.Request(id,base.project,1,"new-upload",held.sha256(),base.prepared.id(),"moderator",Map.of(),true),()->true).rule());
    }
    @Test void revokedPermissionDuringConfigurationCannotRetainDecision(){
        base.check();var allowed=new AtomicBoolean(true);var spyClient=spy(base.base.base.base.fixture.client);doAnswer(call->{var result=call.callRealMethod();allowed.set(false);return result;}).when(spyClient).configuration(any(),any());
        var restricted=new ProjectMutationAdmissionPreparation(base.base.base.base.fixture.mongo,base.budget,base.base.base.base.archive,base.history,prior,base.accounting,spyClient,clock,60000);
        assertThrows(SecurityException.class,()->restricted.prepare(request(false),allowed::get));assertNull(base.base.base.base.archive.find(id));
    }
}
