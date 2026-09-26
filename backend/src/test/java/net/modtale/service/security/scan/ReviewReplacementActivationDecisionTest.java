package net.modtale.service.security.scan;

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
class ReviewReplacementActivationDecisionTest {
    ReviewReplacementJobAccountingTest base=new ReviewReplacementJobAccountingTest();
    Clock clock=Clock.fixed(Instant.now(),ZoneOffset.UTC);
    String id=UUID.randomUUID().toString();ReviewReplacementActivationDecision decisions;
    @BeforeEach void setup()throws Exception {base.setup();decisions=create(base.fixture.fixture.archive,clock);}
    @AfterEach void cleanup(){base.cleanup();}
    ReviewReplacementActivationDecision create(ReviewSnapshotArchive archive,Clock clock) {
        var f=base.fixture;return new ReviewReplacementActivationDecision(f.fixture.fixture.mongo,archive,f.preparation,base.executor,base.accounting,f.fixture.reader,clock,60000);
    }
    void observe(int code,String state,String workState) {
        var f=base.fixture.fixture.fixture;f.route(exchange->f.reply(exchange,code,state,workState));base.check();
    }
    ReviewReplacementActivationDecision.Prepared prepare(boolean acknowledge) {
        return decisions.prepare(id,base.prepared,base.id,"next-moderator",acknowledge,()->true);
    }

    @Test void completedOriginalNeedsNoAdditionalAcknowledgementAndAllowsADifferentActingModerator() {
        observe(200,"COMPLETED","COMPLETED");var before=base.fixture.fixture.raw();var prepared=prepare(false);
        assertEquals("ORIGINAL_COMPLETED",prepared.rule());assertFalse(prepared.acknowledgedUncertainty());
        assertEquals("next-moderator",base.fixture.fixture.archive.load(id).actorId());assertEquals(prepared,prepare(false));
        assertEquals(prepared,create(base.fixture.fixture.archive,Clock.offset(clock,Duration.ofSeconds(10))).recover(id,"next-moderator",()->true));
        assertEquals(before,base.fixture.fixture.raw());assertEquals("HELD",base.fixture.fixture.fixture.mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS).find().first().get("state"));
        assertEquals(1,base.fixture.fixture.fixture.gets.get());assertEquals(0,base.fixture.fixture.fixture.posts.get());
    }

    @ParameterizedTest @ValueSource(strings={"RUNNING","CANCELLED","EXPIRED","QUEUED","HELD","COMPLETED"})
    void uncertaintyRequiresAnExplicitRetainedAcknowledgement(String state) {
        observe(200,state,null);var before=base.fixture.fixture.raw();
        assertThrows(IllegalStateException.class,()->prepare(false));assertNull(base.fixture.fixture.archive.find(id));
        var prepared=prepare(true);assertEquals("ACKNOWLEDGED_UNCERTAINTY",prepared.rule());assertTrue(prepared.acknowledgedUncertainty());
        assertEquals(before,base.fixture.fixture.raw());assertEquals(1,base.fixture.fixture.fixture.gets.get());
    }

    @ParameterizedTest @ValueSource(ints={404,409,403,503})
    void failedRemoteObservationsCannotSilentlyAuthorizeDuplicateWork(int status) {
        observe(status,"QUEUED",null);assertThrows(IllegalStateException.class,()->prepare(false));
        assertEquals("ACKNOWLEDGED_UNCERTAINTY",prepare(true).rule());
    }

    @Test void retriesCannotExtendExpiryOrRewriteAcknowledgement() {
        observe(200,"COMPLETED","COMPLETED");var prepared=prepare(false);
        assertThrows(IllegalStateException.class,()->prepare(true));
        var later=create(base.fixture.fixture.archive,Clock.offset(clock,Duration.ofSeconds(60)));
        assertThrows(IllegalStateException.class,()->later.prepare(id,base.prepared,base.id,"next-moderator",false,()->true));
        assertEquals(prepared,later.recover(id,"next-moderator",()->true));
    }

    @ParameterizedTest @ValueSource(strings={"version","admission","actor"})
    void changedStateOrActorCannotBeSubstitutedOnRetry(String changed) {
        observe(200,"COMPLETED","COMPLETED");var prepared=prepare(false);var f=base.fixture.fixture.fixture;
        switch(changed) {
            case "version" -> f.change("changelog","concurrent metadata edit");
            case "admission" -> f.mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS).updateOne(new Document("_id",base.prepared.id()),new Document("$set",new Document("state","READY")));
            case "actor" -> {assertThrows(IllegalStateException.class,()->decisions.prepare(id,base.prepared,base.id,"other",false,()->true));return;}
        }
        assertThrows(IllegalStateException.class,()->prepare(false));assertEquals(prepared,decisions.recover(id,"next-moderator",()->true));
    }

    @Test void lostArchiveReplyRemainsRecoverableWithoutAnotherObservation() {
        observe(200,"COMPLETED","COMPLETED");var archive=spy(base.fixture.fixture.archive);
        doAnswer(call->{call.callRealMethod();throw new IllegalStateException("lost reply");}).when(archive).retain(any());
        assertThrows(IllegalStateException.class,()->create(archive,clock).prepare(id,base.prepared,base.id,"next-moderator",false,()->true));
        assertEquals(decisions.recover(id,"next-moderator",()->true),prepare(false));assertEquals(1,base.fixture.fixture.fixture.gets.get());
    }

    @Test void permissionRevocationDuringRetentionPreventsReturningADecision() {
        observe(200,"COMPLETED","COMPLETED");var archive=spy(base.fixture.fixture.archive);var allowed=new java.util.concurrent.atomic.AtomicBoolean(true);
        doAnswer(call->{var retained=call.callRealMethod();allowed.set(false);return retained;}).when(archive).retain(any());
        assertThrows(SecurityException.class,()->create(archive,clock).prepare(id,base.prepared,base.id,"next-moderator",false,allowed::get));
        assertEquals("HELD",base.fixture.fixture.fixture.mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS).find().first().get("state"));
    }

    @Test void signedPayloadCannotClaimCompletionForAnUncertainObservation() {
        observe(200,"CANCELLED","CANCELLED");prepare(true);var source=base.fixture.fixture.archive.load(id);
        var body=new org.bson.RawBsonDocument(source.versionBytes()).decode(new org.bson.codecs.DocumentCodec());
        body.put("rule","ORIGINAL_COMPLETED");body.put("acknowledgedUncertainty",false);
        var buffer=new org.bson.RawBsonDocument(body,new org.bson.codecs.DocumentCodec()).getByteBuffer().asNIO();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);
        String other=UUID.randomUUID().toString();
        base.fixture.fixture.archive.retain(new ReviewSnapshotArchive.Snapshot(other,source.projectId(),source.versionIndex(),source.actorId(),source.action(),source.createdAt(),source.expiresAt(),bytes));
        assertThrows(IllegalStateException.class,()->decisions.recover(other,"next-moderator",()->true));
    }

    @Test void observationMutationAfterSigningCannotRewriteTheSameDecision() {
        observe(200,"COMPLETED","COMPLETED");var prepared=prepare(false);
        base.fixture.fixture.fixture.mongo.getCollection(ReviewReplacementJobAccounting.COLLECTION).updateOne(new Document("_id",base.id),
                new Document("$set",new Document("observation.status.workState","RUNNING")));
        assertThrows(IllegalStateException.class,()->prepare(false));assertThrows(IllegalStateException.class,()->prepare(true));
        assertEquals(prepared,decisions.recover(id,"next-moderator",()->true));
    }
    @Test void unfinishedObservationCannotBeUsedForActivationEvenWithAcknowledgement() {
        observe(200,"COMPLETED","COMPLETED");
        base.fixture.fixture.fixture.mongo.getCollection(ReviewReplacementJobAccounting.COLLECTION).updateOne(new Document("_id",base.id),
                new Document("$set",new Document("state","READING")).append("$unset",new Document("observation","").append("receivedAt","")));
        assertThrows(IllegalStateException.class,()->prepare(true));assertNull(base.fixture.fixture.archive.find(id));
    }
}
