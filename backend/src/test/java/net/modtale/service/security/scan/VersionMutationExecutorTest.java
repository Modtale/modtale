package net.modtale.service.security.scan;

import com.mongodb.*;
import com.mongodb.client.ClientSession;
import net.modtale.model.project.*;
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
class VersionMutationExecutorTest {
    VersionMutationPreparationTest base=new VersionMutationPreparationTest();ReviewRepairJournal journal;
    @BeforeEach void setup()throws Exception{base.setup();journal=new ReviewRepairJournal(base.base.fixture.mongo,base.base.archive);}
    @AfterEach void cleanup(){base.cleanup();}
    VersionMutationExecutor executor(){return new VersionMutationExecutor(base.base.fixture.mongo,base.service,base.base.archive,journal,base.base.reader);}
    ProjectVersion version(){return base.base.fixture.mongo.findById(base.base.fixture.project,Project.class).getVersions().getFirst();}
    @Test void contextMutationAndReferenceCommitTogetherWithoutDispatch(){
        var request=base.request(false);var prepared=base.service.prepare(request,()->true);var result=executor().apply(prepared,"owner",()->true);
        assertEquals("APPLIED",result.state());var v=version();assertEquals(List.of("new-runtime"),v.getGameVersions());
        assertEquals("MUTATION_HELD",v.getScanResult().getScanState());assertEquals(2,v.getScanResult().getScanAttempt());
        assertEquals(prepared.id(),v.getVersionMutation().operationId());assertEquals(ProjectVersion.ReviewStatus.PENDING,v.getReviewStatus());
        assertNull(v.getApprovedSecurityEvidence());assertEquals(result,executor().apply(prepared,"owner",()->true));
        assertEquals(1,base.base.fixture.mongo.getCollection(VersionMutationExecutor.REFERENCES).countDocuments());
        assertTrue(new RemoteReviewDiscovery(base.base.fixture.mongo).page(null,64).candidates().isEmpty());
        var writer=new VersionReviewPersistence(base.base.fixture.mongo);var snap=writer.capture(base.base.fixture.project,"v",VersionReviewSnapshot.token(v));
        v.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->writer.apply(snap,v));
        assertEquals(0,base.base.fixture.posts.get());assertEquals(0,base.base.fixture.gets.get());
    }
    @Test void removalRetainsDiscoverableOriginalEvidence(){
        var f=base.base.fixture;f.mongo.getCollection("projects").updateOne(new Document("_id",base.base.raw().get("_id")),new Document("$set",new Document("status","PRIVATE")));
        var request=base.request(true);var prepared=base.service.prepare(request,()->true);assertEquals("APPLIED",executor().apply(prepared,"owner",()->true).state());
        assertTrue(f.mongo.findById(f.project,Project.class).getVersions().isEmpty());var reference=f.mongo.getCollection(VersionMutationExecutor.REFERENCES).find().first();
        assertEquals("REMOVED",reference.get("state"));assertEquals(prepared.beforeArchiveId(),reference.get("beforeArchiveId"));
        assertNotNull(base.service.recover(prepared.id(),"owner",()->true).before());assertEquals("APPLIED",executor().apply(prepared,"owner",()->true).state());
    }
    @Test void originalBlockSurvivesAnOwnerContextChange(){base.base.fixture.change("scanResult.verdict","BLOCK");var prepared=base.service.prepare(base.request(false),()->true);assertEquals("APPLIED",executor().apply(prepared,"owner",()->true).state());assertEquals(prepared.id(),version().getReplacementSecurityHold());assertEquals("BLOCK",version().getScanResult().getVerdict());}
    @Test void ownerProposalCannotModifyNonEditableAuthorityFields(){
        var r=base.request(false);var desired=new RawBsonDocument(r.proposedVersion()).decode(new DocumentCodec());desired.put("hash","d".repeat(64));
        var changed=new VersionMutationPreparation.Request(r.id(),r.projectId(),0,"v",r.expectedSha256(),"owner",r.mutation(),0,VersionMutationPreparationTest.bytes(desired));
        var prepared=base.service.prepare(changed,()->true);var before=base.base.raw();assertThrows(IllegalStateException.class,()->executor().apply(prepared,"owner",()->true));
        assertEquals(before,base.base.raw());assertEquals(0,base.base.fixture.mongo.getCollection(ReviewRepairJournal.COLLECTION).countDocuments());
    }
    @Test void withdrawnLifecycleRecordsNoMutation(){var prepared=base.service.prepare(base.request(false),()->true);base.base.fixture.mongo.getCollection("projects").updateOne(new Document("_id",base.base.raw().get("_id")),new Document("$set",new Document("status","DELETED")));assertEquals("NOT_APPLIED",executor().apply(prepared,"owner",()->true).state());assertEquals(0,base.base.fixture.mongo.getCollection(VersionMutationExecutor.REFERENCES).countDocuments());}
    @Test void changedVersionRecordsNoMutation(){var prepared=base.service.prepare(base.request(false),()->true);base.base.fixture.change("changelog","concurrent");assertEquals("NOT_APPLIED",executor().apply(prepared,"owner",()->true).state());assertEquals("concurrent",version().getChangelog());}
    @Test void authorityLossAfterWriteRollsBackVersionAndReference(){
        var prepared=base.service.prepare(base.request(false),()->true);var reader=spy(base.base.reader);var allowed=new AtomicBoolean(true);var before=base.base.raw();
        doAnswer(call->{var value=(RawReviewSnapshotReader.Captured)call.callRealMethod();if(!value.sha256().equals(prepared.beforeSha256()))allowed.set(false);return value;})
                .when(reader).capture(any(ClientSession.class),eq(before.get("_id")),eq(0),eq("v"));
        var writer=new VersionMutationExecutor(base.base.fixture.mongo,base.service,base.base.archive,journal,reader);
        assertThrows(SecurityException.class,()->writer.apply(prepared,"owner",allowed::get));assertEquals(before,base.base.raw());assertEquals(0,base.base.fixture.mongo.getCollection(VersionMutationExecutor.REFERENCES).countDocuments());
    }
    @Test void lostCommitReplyRecoversOneAppliedMutation(){
        var prepared=base.service.prepare(base.request(false),()->true);var mongo=spy(base.base.fixture.mongo);var factory=spy(mongo.getMongoDatabaseFactory());doReturn(factory).when(mongo).getMongoDatabaseFactory();
        doAnswer(call->{var session=spy((ClientSession)call.callRealMethod());doAnswer(commit->{commit.callRealMethod();throw new MongoException("lost commit reply");}).when(session).commitTransaction();return session;}).when(factory).getSession(any(ClientSessionOptions.class));
        var result=new VersionMutationExecutor(mongo,base.service,base.base.archive,journal,base.base.reader).apply(prepared,"owner",()->true);
        assertEquals("APPLIED",result.state());assertEquals(result,executor().apply(prepared,"owner",()->true));assertEquals(1,mongo.getCollection(VersionMutationExecutor.REFERENCES).countDocuments());
    }
    @Test void exhaustedOrCoercibleAttemptCannotResetTheScanCounter(){
        for(Object attempt:List.of(Integer.MAX_VALUE,4294967297L,1.0,"1")){
            base.base.fixture.change("scanResult.scanAttempt",attempt);var prepared=base.service.prepare(base.request(false),()->true);
            assertThrows(IllegalStateException.class,()->executor().apply(prepared,"owner",()->true));
        }
        assertEquals(0,base.base.fixture.mongo.getCollection(VersionMutationExecutor.REFERENCES).countDocuments());
    }
    @Test void mutationPointerIsPrivateAndFencesManualRescan()throws Exception{
        var prepared=base.service.prepare(base.request(false),()->true);executor().apply(prepared,"owner",()->true);var version=version();
        String token=VersionReviewSnapshot.token(version);var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        assertFalse(mapper.writeValueAsString(version).contains("versionMutation"));
        var writer=new VersionReviewPersistence(base.base.fixture.mongo);var snapshot=writer.captureForRescan(base.base.fixture.project,"v",VersionReviewSnapshot.rescanToken(version));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->writer.queueRescan(snapshot,new ScanResult()));
        version.setVersionMutation(null);assertNotEquals(token,VersionReviewSnapshot.token(version));
    }

}
