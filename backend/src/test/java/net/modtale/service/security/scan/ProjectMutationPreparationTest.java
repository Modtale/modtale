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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationPreparationTest {
    RemoteReviewIsolationIntegrationTest base=new RemoteReviewIsolationIntegrationTest();Clock clock=Clock.fixed(Instant.now(),ZoneOffset.UTC);ProjectMutationPreparation service;
    @BeforeEach void setup()throws Exception{base.setup();base.fixture.attached(30000);base.fixture.mongo.getCollection("projects").updateOne(new Document("_id",base.raw().get("_id")),new Document("$push",new Document("versions",new Document("_id","w").append("reviewStatus","PENDING"))));service=create(base.archive,clock);}
    @AfterEach void cleanup(){base.cleanup();}
    ProjectMutationPreparation create(ReviewSnapshotArchive archive,Clock time){return new ProjectMutationPreparation(base.fixture.mongo,archive,time,60000);}
    ProjectMutationPreparation.Request request(){var captured=service.capture(base.raw().get("_id"),()->true);var next=new RawBsonDocument(captured.bytes()).decode(new DocumentCodec());next.put("versions",List.of(next.getList("versions",Document.class).get(1),new Document("_id","new-upload").append("reviewStatus","PENDING")));return new ProjectMutationPreparation.Request(UUID.randomUUID().toString(),captured.projectId(),captured.sha256(),"owner",ProjectMutationPreparation.Mutation.VERSION_LIST,VersionMutationPreparationTest.bytes(next));}
    @Test void wholeGroupRetainsRemovedAddedAndMovedVersionsWithoutMutation(){
        var request=request();var before=base.raw();var prepared=service.prepare(request,()->true);assertEquals(prepared,service.prepare(request,()->true));var recovered=service.recover(prepared.id(),"owner",()->true);
        assertArrayEquals(service.capture(request.projectId(),()->true).bytes(),recovered.before().versionBytes());assertArrayEquals(request.proposedProject(),recovered.after().versionBytes());
        assertEquals(3,recovered.transitions().size());assertTrue(recovered.transitions().stream().anyMatch(t->t.changes().contains(VersionReviewTransition.Change.REMOVED)));
        assertTrue(recovered.transitions().stream().anyMatch(t->t.changes().contains(VersionReviewTransition.Change.ADDED)));assertEquals(before,base.raw());assertEquals(0,base.fixture.posts.get());
    }
    @ParameterizedTest @ValueSource(strings={"actor","subset","rootAuthority"})
    void proposalCannotBeRetargetedOrSubsetted(String change){
        var request=request();var prepared=service.prepare(request,()->true);var next=new RawBsonDocument(request.proposedProject()).decode(new DocumentCodec());
        if(change.equals("subset"))next.put("versions",List.of(next.getList("versions",Document.class).getFirst()));if(change.equals("rootAuthority"))next.put("authorId","different-owner");
        var retry=new ProjectMutationPreparation.Request(request.id(),request.projectId(),request.expectedSha256(),change.equals("actor")?"another":request.actor(),request.mutation(),VersionMutationPreparationTest.bytes(next));
        assertThrows(RuntimeException.class,()->service.prepare(retry,()->true));assertEquals(prepared,service.recover(prepared.id(),"owner",()->true).prepared());
    }
    @Test void ownershipChangesAreRejectedBeforeRetention(){var request=request();var next=new RawBsonDocument(request.proposedProject()).decode(new DocumentCodec());next.put("authorId","different");assertThrows(IllegalStateException.class,()->service.prepare(new ProjectMutationPreparation.Request(request.id(),request.projectId(),request.expectedSha256(),request.actor(),request.mutation(),VersionMutationPreparationTest.bytes(next)),()->true));assertEquals(0,base.fixture.mongo.getCollection(ReviewSnapshotArchive.METADATA).countDocuments());}
    @Test void submissionBindsLifecycleAndTheCompleteVersionSet(){
        var id=base.raw().get("_id");base.fixture.mongo.getCollection("projects").updateOne(new Document("_id",id),new Document("$set",new Document("status","DRAFT")));
        var captured=service.capture(id,()->true);var next=new RawBsonDocument(captured.bytes()).decode(new DocumentCodec());next.put("status","PENDING");
        var request=new ProjectMutationPreparation.Request(UUID.randomUUID().toString(),id,captured.sha256(),"owner",ProjectMutationPreparation.Mutation.SUBMISSION,VersionMutationPreparationTest.bytes(next));
        var prepared=service.prepare(request,()->true);assertEquals(2,service.recover(prepared.id(),"owner",()->true).transitions().size());
        next.put("versions",List.of(next.getList("versions",Document.class).getFirst()));
        assertThrows(IllegalStateException.class,()->service.prepare(new ProjectMutationPreparation.Request(UUID.randomUUID().toString(),id,captured.sha256(),"owner",request.mutation(),VersionMutationPreparationTest.bytes(next)),()->true));
    }
    @Test void rootChangeAfterRetentionPreventsSuccessfulPreparation(){var request=request();var archive=spy(base.archive);doAnswer(call->{var result=call.callRealMethod();base.fixture.mongo.getCollection("projects").updateOne(new Document("_id",request.projectId()),new Document("$set",new Document("authorId","changed")));return result;}).when(archive).retain(any());assertThrows(IllegalStateException.class,()->create(archive,clock).prepare(request,()->true));}
    @Test void historyRecoversAfterDeletionButExpiredPreparationCannotRestart(){var request=request();var prepared=service.prepare(request,()->true);var later=create(base.archive,Clock.offset(clock,Duration.ofMinutes(2)));assertThrows(IllegalStateException.class,()->later.prepare(request,()->true));base.fixture.mongo.getCollection("projects").deleteMany(new Document());assertEquals(prepared,later.recover(prepared.id(),"owner",()->true).prepared());assertThrows(SecurityException.class,()->later.recover(prepared.id(),"owner",()->false));}
    @ParameterizedTest @ValueSource(strings={"BEFORE","AFTER","INTENT"})
    void lostAcknowledgementRecoversOnlyTheSameWholeGroup(String part){var archive=spy(base.archive);var once=new java.util.concurrent.atomic.AtomicBoolean();doAnswer(call->{var result=(ReviewSnapshotArchive.Snapshot)call.callRealMethod();if(result.action().name().endsWith(part)&&!once.getAndSet(true))throw new IllegalStateException("lost reply");return result;}).when(archive).retain(any());var request=request();var prepared=create(archive,clock).prepare(request,()->true);assertTrue(once.get());assertEquals(prepared,service.prepare(request,()->true));}
    @Test void tamperedGroupPartFailsRecovery(){var prepared=service.prepare(request(),()->true);base.fixture.mongo.getCollection(ReviewSnapshotArchive.METADATA).updateOne(new Document("_id",prepared.afterArchiveId()),new Document("$set",new Document("projectId","other")));assertThrows(IllegalStateException.class,()->service.recover(prepared.id(),"owner",()->true));}
    @Test void duplicateVersionIdsAndCrossProjectRootsCannotBeSigned(){
        var r=request();var next=new RawBsonDocument(r.proposedProject()).decode(new DocumentCodec());var first=next.getList("versions",Document.class).getFirst();next.put("versions",List.of(first,first));var duplicate=next;
        assertThrows(IllegalArgumentException.class,()->service.prepare(new ProjectMutationPreparation.Request(r.id(),r.projectId(),r.expectedSha256(),r.actor(),r.mutation(),VersionMutationPreparationTest.bytes(duplicate)),()->true));
        next=new RawBsonDocument(r.proposedProject()).decode(new DocumentCodec());next.put("_id","different-project");var cross=VersionMutationPreparationTest.bytes(next);
        assertThrows(IllegalStateException.class,()->service.prepare(new ProjectMutationPreparation.Request(r.id(),r.projectId(),r.expectedSha256(),r.actor(),r.mutation(),cross),()->true));
    }
    @Test void captureAndRequestDoNotExposeMutableSnapshotBytes(){
        var r=request();var captured=service.capture(r.projectId(),()->true);var data=captured.bytes();data[0]=0;assertNotEquals(0,captured.bytes()[0]);
        var proposed=r.proposedProject();proposed[0]=0;assertNotEquals(0,r.proposedProject()[0]);
        assertThrows(SecurityException.class,()->service.capture(r.projectId(),()->false));
    }

}
