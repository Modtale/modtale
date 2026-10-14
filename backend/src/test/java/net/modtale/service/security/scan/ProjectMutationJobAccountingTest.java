package net.modtale.service.security.scan;

import net.modtale.service.admin.review.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ProjectMutationJobAccountingTest {
    ProjectMutationExecutorTest base=new ProjectMutationExecutorTest();ProjectMutationReferenceReader history;ReviewRepairWorkflow budget;
    ProjectMutationPreparation.Prepared prepared;ProjectMutationJobAccounting accounting;Object project;
    String id=UUID.randomUUID().toString();AtomicInteger gets=new AtomicInteger();
    @BeforeEach void setup()throws Exception{
        base.setup();project=base.root().get("_id");prepared=base.base.service.prepare(base.base.request(),()->true);assertEquals("APPLIED",base.executor().apply(prepared,"owner",()->true).state());
        history=new ProjectMutationReferenceReader(base.base.base.fixture.mongo,base.base.base.archive,base.base.service,base.executor());history.initialize();
        budget=new ReviewRepairWorkflow(mock(ReviewRepairPreparation.class),mock(ReviewIsolationExecutor.class),1);accounting=create();
    }
    ProjectMutationJobAccounting create(){return new ProjectMutationJobAccounting(base.base.base.fixture.mongo,budget,history,base.base.base.fixture.client,1);}
    @AfterEach void cleanup(){if(accounting!=null)accounting.close();if(budget!=null)budget.close();base.cleanup();}
    ProjectMutationJobAccounting.Receipt check(){return accounting.check(id,project,prepared.id(),"v",()->true);}
    void route(int code,String state){var f=base.base.base.fixture;f.route(exchange->{assertEquals("GET",exchange.getRequestMethod());assertTrue(exchange.getRequestURI().getPath().endsWith("/"+f.job));gets.incrementAndGet();f.reply(exchange,code,state);});}
    @ParameterizedTest @ValueSource(strings={"COMPLETED","CANCELLED","RUNNING","QUEUED","EXPIRED","HELD"})
    void originalJobStateIsRetainedWithoutActivatingNewWork(String state){
        route(200,state);var before=base.root();var result=check();assertEquals("OBSERVED",result.state());assertEquals(state,result.observation().status().state());
        try(var restarted=create()){assertEquals(result,restarted.check(id,project,prepared.id(),"v",()->true));}
        assertEquals(result,accounting.receipt(id,project,prepared.id(),"v",()->true));assertEquals(1,gets.get());assertEquals(before,base.root());assertEquals(0,base.base.base.fixture.posts.get());
    }
    @ParameterizedTest @ValueSource(ints={404,409,401,403,429,503})
    void missingConflictingAndUnavailableJobsStayDistinct(int code){
        route(code,"QUEUED");var result=check();assertEquals(code==404?"NOT_FOUND":code==409?"CONTEXT_CONFLICT":code==503?"UNKNOWN":"UNAVAILABLE",result.observation().kind());assertEquals(result,check());assertEquals(1,gets.get());
    }
    @Test void deletedProjectCannotRetargetOriginalJob(){
        base.base.base.fixture.mongo.getCollection("projects").deleteMany(new Document());route(200,"COMPLETED");assertEquals(base.base.base.fixture.job,check().observation().status().jobId());assertEquals(1,gets.get());
    }
    @Test void alteredGroupReceiptOrDifferentVersionCannotCreateObservation(){
        assertThrows(RuntimeException.class,()->accounting.check(id,project,prepared.id(),"new-upload",()->true));
        assertThrows(RuntimeException.class,()->accounting.check(id,project,prepared.id(),"w",()->true));
        base.base.base.fixture.mongo.getCollection(ReviewRepairJournal.COLLECTION).updateOne(new Document("_id",prepared.id()),new Document("$set",new Document("state","UNKNOWN")));
        assertThrows(RuntimeException.class,this::check);assertEquals(0,base.base.base.fixture.mongo.getCollection(ProjectMutationJobAccounting.COLLECTION).countDocuments());assertEquals(0,gets.get());
    }
    @Test void lostHttpReplyIsNotRetried(){
        base.base.base.fixture.route(exchange->{gets.incrementAndGet();exchange.close();});var result=check();assertEquals("UNKNOWN",result.state());assertEquals(result,check());assertEquals(1,gets.get());
    }
    @Test void deniedPermissionAndSharedShutdownPreventRemoteObservation(){
        assertThrows(SecurityException.class,()->accounting.check(id,project,prepared.id(),"v",()->false));budget.close();assertThrows(IllegalStateException.class,this::check);assertEquals(0,gets.get());
    }
    @Test void observationIdentityCannotBeReusedForAnotherGroupEvenWithSameRemoteJob(){
        route(200,"COMPLETED");check();var original=new org.bson.RawBsonDocument(base.base.base.archive.load(prepared.beforeArchiveId()).versionBytes()).decode(new org.bson.codecs.DocumentCodec());
        base.base.base.fixture.mongo.getCollection("projects").replaceOne(new Document("_id",project),original);
        var next=base.base.service.prepare(base.base.request(),()->true);assertEquals("APPLIED",base.executor().apply(next,"owner",()->true).state());
        assertThrows(RuntimeException.class,()->accounting.check(id,project,next.id(),"v",()->true));assertEquals(1,gets.get());
    }

}
