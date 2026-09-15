package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewReplacementJobAccountingTest {
    ReviewReplacementPreparationTest fixture=new ReviewReplacementPreparationTest();
    ReviewReplacementExecutor executor;
    ReviewReplacementPreparation.Prepared prepared;
    ReviewReplacementJobAccounting accounting;
    String id=UUID.randomUUID().toString();AtomicInteger gets=new AtomicInteger();
    @BeforeEach void setup()throws Exception {
        fixture.setup();executor=new ReviewReplacementExecutor(fixture.fixture.fixture.mongo,fixture.preparation,fixture.fixture.reader,
                new ReviewRepairJournal(fixture.fixture.fixture.mongo,fixture.fixture.archive));
        prepared=fixture.preparation.prepare(fixture.request(),()->true);
        assertEquals("APPLIED",executor.stage(prepared,"new-moderator",()->true).state());accounting=create();
    }
    ReviewReplacementJobAccounting create() {
        return new ReviewReplacementJobAccounting(fixture.fixture.fixture.mongo,fixture.preparation,executor,fixture.targets,
                fixture.fixture.fixture.client,1,Duration.ofSeconds(10));
    }
    @AfterEach void cleanup(){accounting.close();fixture.cleanup();}
    ReviewReplacementJobAccounting.Receipt check(){return accounting.check(id,prepared,"new-moderator",()->true);}
    void route(int code,String state) {
        var f=fixture.fixture.fixture;f.route(exchange->{assertEquals("GET",exchange.getRequestMethod());
            assertTrue(exchange.getRequestURI().getPath().endsWith("/"+f.job));gets.incrementAndGet();f.reply(exchange,code,state);});
    }

    @ParameterizedTest @ValueSource(strings={"COMPLETED","CANCELLED","RUNNING","QUEUED","EXPIRED","HELD"})
    void retainsDistinctOriginalStatusWithoutActivatingReplacement(String state) {
        route(200,state);var before=fixture.fixture.raw();var result=check();
        assertEquals("OBSERVED",result.state());assertEquals(state,result.observation().status().state());
        assertEquals(fixture.fixture.fixture.job,result.observation().status().jobId());assertNotNull(result.receivedAt());
        try(var restarted=create()){assertEquals(result,restarted.check(id,prepared,"new-moderator",()->true));}
        assertEquals(result,accounting.receipt(id,prepared,"new-moderator",()->true));assertEquals(1,gets.get());
        assertEquals(before,fixture.fixture.raw());assertEquals("HELD",fixture.fixture.fixture.mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS).find().first().get("state"));
        assertEquals(0,fixture.fixture.fixture.posts.get());
    }

    @ParameterizedTest @ValueSource(ints={404,409,401,403,429,503})
    void missingWrongOriginAndUnavailableRemainSeparateUncertainties(int code) {
        route(code,"QUEUED");var result=check();
        String expected=code==404?"NOT_FOUND":code==409?"CONTEXT_CONFLICT":code==503?"UNKNOWN":"UNAVAILABLE";
        assertEquals(expected,result.observation().kind());assertEquals(code,result.observation().httpStatus());
        assertEquals(result,check());assertEquals(1,gets.get());
    }

    @Test void currentVersionDeletionCannotRetargetTheArchivedJob() {
        fixture.fixture.fixture.mongo.getCollection("projects").deleteMany(new Document());route(200,"COMPLETED");
        assertEquals(fixture.fixture.fixture.job,check().observation().status().jobId());assertEquals(1,gets.get());
    }

    @Test void anUnappliedOrTamperedProposalCannotCreateAnObservation() {
        var collection=fixture.fixture.fixture.mongo.getCollection(ReviewRepairJournal.COLLECTION);
        collection.updateOne(new Document("_id",prepared.beforeArchiveId()),new Document("$set",new Document("state","UNKNOWN")));
        assertThrows(IllegalStateException.class,this::check);
        assertEquals(0,fixture.fixture.fixture.mongo.getCollection(ReviewReplacementJobAccounting.COLLECTION).countDocuments());assertEquals(0,gets.get());
    }

    @Test void deniedOrDifferentActorCannotObserveTheOriginalJob() {
        assertThrows(SecurityException.class,()->accounting.check(id,prepared,"new-moderator",()->false));
        assertThrows(IllegalStateException.class,()->accounting.check(id,prepared,"other",()->true));
        assertEquals(0,fixture.fixture.fixture.mongo.getCollection(ReviewReplacementJobAccounting.COLLECTION).countDocuments());assertEquals(0,gets.get());
    }

    @Test void lostHttpReplyIsRetainedWithoutAnotherGet() {
        fixture.fixture.fixture.route(exchange->{gets.incrementAndGet();exchange.close();});
        var result=check();assertEquals("UNKNOWN",result.state());assertEquals("UNKNOWN",result.observation().kind());
        assertEquals(result,check());assertEquals(1,gets.get());
    }
}
