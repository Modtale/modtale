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
class VersionMutationPreparationTest {
    RemoteReviewIsolationIntegrationTest base=new RemoteReviewIsolationIntegrationTest();
    Clock clock=Clock.fixed(Instant.now(),ZoneOffset.UTC);VersionMutationPreparation service;
    @BeforeEach void setup()throws Exception {base.setup();base.fixture.attached(30000);service=create(base.archive,clock);}
    @AfterEach void cleanup(){base.cleanup();}
    VersionMutationPreparation create(ReviewSnapshotArchive archive,Clock time){return new VersionMutationPreparation(base.reader,archive,time,60000);}
    VersionMutationPreparation.Request request(boolean removal) {
        var captured=base.reader.capture(base.raw().get("_id"),0,"v");var next=new RawBsonDocument(captured.versionBytes()).decode(new DocumentCodec());
        next.put("gameVersions",List.of("new-runtime"));next.put("scanResult",new Document("scanState","QUEUED").append("scanRequestId",UUID.randomUUID().toString()));
        return new VersionMutationPreparation.Request(UUID.randomUUID().toString(),captured.projectId(),0,"v",captured.sha256(),"owner",
                removal?VersionMutationPreparation.Mutation.REMOVAL:VersionMutationPreparation.Mutation.CONTEXT_EDIT,removal?-1:0,removal?null:bytes(next));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void retainsExactBeforeAndProposedBytesWithoutApplyingMutation(boolean removal) {
        var request=request(removal);var before=base.raw();var prepared=service.prepare(request,()->true);
        assertEquals(prepared,service.prepare(request,()->true));var recovered=service.recover(request.id(),"owner",()->true);
        assertEquals(prepared,recovered.prepared());assertArrayEquals(base.reader.capture(request.projectId(),0,"v").versionBytes(),recovered.before().versionBytes());
        if(removal)assertNull(recovered.after());else assertArrayEquals(request.proposedVersion(),recovered.after().versionBytes());
        assertEquals(before,base.raw());assertEquals(0,base.fixture.posts.get());assertEquals(0,base.fixture.gets.get());
    }
    @Test void recoveryAfterDeletionAndExpiryDoesNotRefreshOrRecapture() {
        var request=request(false);var prepared=service.prepare(request,()->true);base.fixture.mongo.getCollection("projects").deleteMany(new Document());
        var later=create(base.archive,Clock.offset(clock,Duration.ofDays(1)));
        assertEquals(prepared,later.recover(request.id(),"owner",()->true).prepared());
        assertThrows(RuntimeException.class,()->later.prepare(request,()->true));assertThrows(IllegalStateException.class,()->later.recover(request.id(),"another",()->true));
    }
    @ParameterizedTest @ValueSource(strings={"actor","desired","mutation"})
    void retryCannotReplaceTheProposedMutation(String change) {
        var request=request(false);var prepared=service.prepare(request,()->true);var next=new RawBsonDocument(request.proposedVersion()).decode(new DocumentCodec());next.put("changelog","different");
        var retry=new VersionMutationPreparation.Request(request.id(),request.projectId(),0,"v",request.expectedSha256(),change.equals("actor")?"another":request.actor(),
                change.equals("mutation")?VersionMutationPreparation.Mutation.TARGET_REPLACEMENT:request.mutation(),0,change.equals("desired")?bytes(next):request.proposedVersion());
        assertThrows(RuntimeException.class,()->service.prepare(retry,()->true));assertEquals(prepared,service.recover(request.id(),"owner",()->true).prepared());
    }
    @ParameterizedTest @ValueSource(strings={"before","after","intent"})
    void lostArchiveReplyRecoversTheSameProposal(String part) {
        var archive=spy(base.archive);var once=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{var value=(ReviewSnapshotArchive.Snapshot)call.callRealMethod();
            if(value.action().name().endsWith(part.toUpperCase(Locale.ROOT)) && !once.getAndSet(true))throw new IllegalStateException("lost archive acknowledgement");return value;})
                .when(archive).retain(any());
        var request=request(false);var prepared=create(archive,clock).prepare(request,()->true);assertTrue(once.get());assertEquals(prepared,service.prepare(request,()->true));
    }
    @ParameterizedTest @ValueSource(strings={"before","after","intent"})
    void tamperedArchiveCannotBeRecovered(String part) {
        var request=request(false);var prepared=service.prepare(request,()->true);var id=switch(part){case "before"->prepared.beforeArchiveId();case "after"->prepared.afterArchiveId();default->prepared.id();};
        base.fixture.mongo.getCollection(ReviewSnapshotArchive.METADATA).updateOne(new Document("_id",id),new Document("$set",new Document("actorId","different")));
        assertThrows(IllegalStateException.class,()->service.recover(request.id(),"owner",()->true));
    }
    @Test void changedVersionAndRevokedAccessCannotYieldAnApplicablePreparation() {
        var request=request(false);var archive=spy(base.archive);
        doAnswer(call->{var value=call.callRealMethod();base.fixture.change("changelog","concurrent");return value;}).when(archive).retain(any());
        assertThrows(IllegalStateException.class,()->create(archive,clock).prepare(request,()->true));
        assertThrows(SecurityException.class,()->service.recover(request.id(),"owner",()->false));
        assertEquals("concurrent",base.raw().getList("versions",Document.class).getFirst().get("changelog"));
    }
    @Test void unchangedOrMetadataOnlyMutationDoesNotRetainAnything() {
        var request=request(false);var old=base.reader.capture(request.projectId(),0,"v");var next=new RawBsonDocument(old.versionBytes()).decode(new DocumentCodec());next.put("changelog","metadata only");
        var metadata=new VersionMutationPreparation.Request(request.id(),request.projectId(),0,"v",request.expectedSha256(),"owner",request.mutation(),0,bytes(next));
        assertThrows(IllegalArgumentException.class,()->service.prepare(metadata,()->true));assertEquals(0,base.fixture.mongo.getCollection(ReviewSnapshotArchive.METADATA).countDocuments());
    }
    @Test void expiryCannotBeExtendedByRetryingAnExistingProposal() {
        var request=request(false);var prepared=service.prepare(request,()->true);
        var later=create(base.archive,Clock.offset(clock,Duration.ofMinutes(2)));
        assertThrows(IllegalStateException.class,()->later.prepare(request,()->true));assertEquals(prepared,later.recover(request.id(),"owner",()->true).prepared());
    }
    @Test void malformedProposedBytesCannotBeSignedAndRequestBytesAreDefensive() {
        var request=request(false);var original=request.proposedVersion();var copy=request.proposedVersion();copy[0]=0;assertArrayEquals(original,request.proposedVersion());
        var trailing=Arrays.copyOf(original,original.length+1);
        var malformed=new VersionMutationPreparation.Request(request.id(),request.projectId(),0,"v",request.expectedSha256(),"owner",request.mutation(),0,trailing);
        assertThrows(RuntimeException.class,()->service.prepare(malformed,()->true));assertEquals(0,base.fixture.mongo.getCollection(ReviewSnapshotArchive.METADATA).countDocuments());
    }
    static byte[] bytes(Document doc){var buffer=new RawBsonDocument(doc,new DocumentCodec()).getByteBuffer().asNIO();var bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;}
}
