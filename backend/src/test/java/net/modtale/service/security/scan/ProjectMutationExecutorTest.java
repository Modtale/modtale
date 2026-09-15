package net.modtale.service.security.scan;

import com.mongodb.*;
import com.mongodb.client.*;
import net.modtale.service.admin.review.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationExecutorTest {
    ProjectMutationPreparationTest base=new ProjectMutationPreparationTest();ReviewRepairJournal journal;
    @BeforeEach void setup()throws Exception{base.setup();journal=new ReviewRepairJournal(base.base.fixture.mongo,base.base.archive);}
    @AfterEach void cleanup(){base.cleanup();}
    ProjectMutationExecutor executor(){return new ProjectMutationExecutor(base.base.fixture.mongo,base.service,base.base.archive,journal);}
    Document root(){return base.base.raw();}
    @Test void replacementGroupCommitsRemovalNewRequestAndPreservedVersionTogether(){
        var unchanged=new Document(root().getList("versions",Document.class).get(1));var prepared=base.service.prepare(base.request(),()->true);
        var result=executor().apply(prepared,"owner",()->true);assertEquals("APPLIED",result.state());var versions=root().getList("versions",Document.class);
        assertEquals(List.of("w","new-upload"),versions.stream().map(v->v.getString("_id")).toList());assertEquals(unchanged,versions.getFirst());
        var scan=versions.get(1).get("scanResult",Document.class);assertEquals("MUTATION_HELD",scan.get("scanState"));assertEquals(1,scan.get("scanAttempt"));
        var ref=base.base.fixture.mongo.getCollection(ProjectMutationExecutor.REFERENCES).find().first();assertEquals(result.afterSha256(),ref.get("afterSha256"));
        assertTrue(ref.getList("versions",Document.class).stream().anyMatch(v->"v".equals(v.get("versionId")) && "REMOVED".equals(v.get("state"))));
        assertEquals(result,executor().apply(prepared,"owner",()->true));assertEquals(1,base.base.fixture.mongo.getCollection(ProjectMutationExecutor.REFERENCES).countDocuments());
        assertEquals(0,base.base.fixture.posts.get());assertEquals(0,base.base.fixture.gets.get());
    }
    @Test void submissionRetainsPrunedHistoryAndUsesDistinctRequests(){
        var f=base.base.fixture;f.change("retainedRemoteReview",f.mongo.getConverter().convertToMongoType(f.saved().getRemoteReview()));f.change("scanResult",null);
        f.mongo.getCollection("projects").updateOne(new Document("_id",root().get("_id")),new Document("$set",new Document("status","DRAFT")));
        var captured=base.service.capture(root().get("_id"),()->true);var proposed=new RawBsonDocument(captured.bytes()).decode(new DocumentCodec());proposed.put("status","PENDING");
        var request=new ProjectMutationPreparation.Request(UUID.randomUUID().toString(),captured.projectId(),captured.sha256(),"owner",ProjectMutationPreparation.Mutation.SUBMISSION,VersionMutationPreparationTest.bytes(proposed));
        var prepared=base.service.prepare(request,()->true);assertEquals("APPLIED",executor().apply(prepared,"owner",()->true).state());assertEquals("PENDING",root().get("status"));
        var versions=root().getList("versions",Document.class);assertEquals(2,versions.getFirst().get("scanResult",Document.class).get("scanAttempt"));
        assertNotEquals(versions.getFirst().get("scanResult",Document.class).get("scanRequestId"),versions.get(1).get("scanResult",Document.class).get("scanRequestId"));
        assertEquals(2,f.mongo.getCollection(ProjectMutationExecutor.REFERENCES).find().first().getList("versions",Document.class).size());
    }
    @Test void changedOwnerOrConcurrentMetadataPreventsTheEntireGroup(){var prepared=base.service.prepare(base.request(),()->true);base.base.fixture.mongo.getCollection("projects").updateOne(new Document("_id",root().get("_id")),new Document("$set",new Document("authorId","changed")));assertEquals("NOT_APPLIED",executor().apply(prepared,"owner",()->true).state());assertEquals(2,root().getList("versions",Document.class).size());assertEquals(0,base.base.fixture.mongo.getCollection(ProjectMutationExecutor.REFERENCES).countDocuments());}
    @Test void lostCommitReplyRecoversWithoutReapplyingAnyChild(){
        var prepared=base.service.prepare(base.request(),()->true);var mongo=spy(base.base.fixture.mongo);var factory=spy(mongo.getMongoDatabaseFactory());doReturn(factory).when(mongo).getMongoDatabaseFactory();
        doAnswer(call->{var session=spy((ClientSession)call.callRealMethod());doAnswer(commit->{commit.callRealMethod();throw new MongoException("lost commit reply");}).when(session).commitTransaction();return session;}).when(factory).getSession(any(ClientSessionOptions.class));
        var result=new ProjectMutationExecutor(mongo,base.service,base.base.archive,journal).apply(prepared,"owner",()->true);assertEquals("APPLIED",result.state());assertEquals(result,executor().apply(prepared,"owner",()->true));
    }
    @Test void revocationAfterReferenceInsertRollsBackEveryVersion(){
        var prepared=base.service.prepare(base.request(),()->true);var before=root();var mongo=spy(base.base.fixture.mongo);var references=spy(mongo.getCollection(ProjectMutationExecutor.REFERENCES));var allowed=new AtomicBoolean(true);
        doReturn(references).when(mongo).getCollection(ProjectMutationExecutor.REFERENCES);
        doAnswer(call->{call.callRealMethod();allowed.set(false);return null;}).when(references).insertOne(any(ClientSession.class),any(Document.class));
        assertThrows(SecurityException.class,()->new ProjectMutationExecutor(mongo,base.service,base.base.archive,journal).apply(prepared,"owner",allowed::get));
        assertEquals(before,root());assertEquals(0,references.countDocuments());
    }
    @Test void newUploadCannotSmuggleApprovedEvidenceIntoTheGroup(){
        var request=base.request();var proposed=new RawBsonDocument(request.proposedProject()).decode(new DocumentCodec());proposed.getList("versions",Document.class).get(1).put("approvedSecurityEvidence",new Document("clearanceGranted",true));
        var changed=new ProjectMutationPreparation.Request(request.id(),request.projectId(),request.expectedSha256(),request.actor(),request.mutation(),VersionMutationPreparationTest.bytes(proposed));
        var prepared=base.service.prepare(changed,()->true);assertThrows(IllegalStateException.class,()->executor().apply(prepared,"owner",()->true));assertEquals(0,base.base.fixture.mongo.getCollection(ProjectMutationExecutor.REFERENCES).countDocuments());
    }
    @Test void groupContextChangePreservesAnExistingSecurityBlock(){
        base.base.fixture.change("scanResult.verdict","BLOCK");var captured=base.service.capture(root().get("_id"),()->true);var proposed=new RawBsonDocument(captured.bytes()).decode(new DocumentCodec());
        proposed.getList("versions",Document.class).getFirst().put("gameVersions",List.of("changed"));
        var request=new ProjectMutationPreparation.Request(UUID.randomUUID().toString(),captured.projectId(),captured.sha256(),"owner",ProjectMutationPreparation.Mutation.VERSION_LIST,VersionMutationPreparationTest.bytes(proposed));
        var prepared=base.service.prepare(request,()->true);assertEquals("APPLIED",executor().apply(prepared,"owner",()->true).state());
        var version=root().getList("versions",Document.class).getFirst();assertEquals(prepared.id(),version.get("replacementSecurityHold"));assertEquals("BLOCK",version.get("scanResult",Document.class).get("verdict"));
        assertEquals(2,version.get("scanResult",Document.class).get("scanAttempt"));
    }
    @Test void failedReferenceInsertCannotLeaveAPartiallyAppliedGroup(){
        var prepared=base.service.prepare(base.request(),()->true);var before=root();base.base.fixture.mongo.getCollection(ProjectMutationExecutor.REFERENCES).insertOne(new Document("_id",prepared.id()).append("unexpected",true));
        assertEquals("UNKNOWN",executor().apply(prepared,"owner",()->true).state());assertEquals(before,root());
        assertEquals(1,base.base.fixture.mongo.getCollection(ProjectMutationExecutor.REFERENCES).countDocuments());
    }

    @Test void newUploadCannotIntroduceUnknownFieldsOrMappingTypes(){
        for(String field:List.of("futureAuthority","_class")){
            var request=base.request();var proposed=new RawBsonDocument(request.proposedProject()).decode(new DocumentCodec());proposed.getList("versions",Document.class).get(1).put(field,"unexpected");
            var changed=new ProjectMutationPreparation.Request(request.id(),request.projectId(),request.expectedSha256(),request.actor(),request.mutation(),VersionMutationPreparationTest.bytes(proposed));
            var prepared=base.service.prepare(changed,()->true);assertThrows(IllegalStateException.class,()->executor().apply(prepared,"owner",()->true));
        }
    }

    @Test void numericBsonTypeChangeCannotPassTheRootComparison(){
        var projects=base.base.fixture.mongo.getCollection("projects");var query=new Document("_id",root().get("_id"));projects.updateOne(query,new Document("$set",new Document("unknownCounter",1)));
        var prepared=base.service.prepare(base.request(),()->true);projects.updateOne(query,new Document("$set",new Document("unknownCounter",1L)));
        assertEquals("NOT_APPLIED",executor().apply(prepared,"owner",()->true).state());assertInstanceOf(Long.class,root().get("unknownCounter"));
    }

}
