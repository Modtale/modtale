package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewRepairClosureTest {
    ReviewIsolationExecutorTest fixture=new ReviewIsolationExecutorTest();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    ReviewRepairPreparation.Prepared intent(long expires) {
        var capture=fixture.reader.capture("p",0,"v");String id=UUID.randomUUID().toString();long created=expires-10000;
        fixture.archive.retain(capture.forArchive(id,"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW,created,expires));
        return new ReviewRepairPreparation.Prepared(id,capture.sha256(),created,expires);
    }
    void record(ReviewRepairPreparation.Prepared p,String state) {
        var d=new Document("_id",p.id()).append("token",UUID.randomUUID().toString()).append("actor","actor").append("action","ISOLATE_REVIEW")
                .append("sha256",p.sha256()).append("createdAt",p.createdAt()).append("expiresAt",p.expiresAt()).append("state",state);
        if(!state.equals("RESERVED"))d.append("startedAt",new Date(p.createdAt()));
        if(state.equals("UNKNOWN"))d.append("uncertainAt",new Date(p.createdAt()+1));
        fixture.mongo.getCollection(ReviewRepairJournal.COLLECTION).insertOne(d);
    }
    void expire(ReviewRepairPreparation.Prepared p)throws InterruptedException {
        long wait=p.expiresAt()-System.currentTimeMillis()+25;if(wait>0)Thread.sleep(wait);
    }
    @ParameterizedTest @ValueSource(strings={"RESERVED","EXECUTING","UNKNOWN"})
    void closesExpiredClaimsWithoutMutatingVersionOrRenewingIntent(String state) {
        var p=intent(System.currentTimeMillis()-1000);record(p,state);var before=fixture.version();
        var receipt=fixture.executor().closeExpired(p,"actor",()->true);assertEquals("NOT_APPLIED",receipt.outcome().state());assertEquals(before,fixture.version());
        var saved=fixture.operation();var resolution=saved.get("resolution",Document.class);assertEquals("EXPIRED_CLAIM_CLOSED",resolution.get("kind"));assertEquals(state,resolution.get("previousState"));
        assertEquals("actor",resolution.get("actorId"));assertInstanceOf(Date.class,resolution.get("closedAt"));assertEquals(saved.get("finishedAt"),resolution.get("closedAt"));
        assertEquals(p.expiresAt(),saved.get("expiresAt"));assertEquals(receipt,fixture.executor().closeExpired(p,"actor",()->true));assertEquals(saved,fixture.operation());
        assertNull(fixture.journal.claim(p,"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW,()->true));assertEquals(before,fixture.version());
    }
    @Test void unexpiredAbsentMalformedAndWrongActorCannotClose() {
        var p=intent(System.currentTimeMillis()+60000);assertEquals("UNKNOWN",fixture.executor().closeExpired(p,"actor",()->true).outcome().state());assertNull(fixture.operation());
        record(p,"EXECUTING");var before=fixture.operation();assertEquals("UNKNOWN",fixture.executor().closeExpired(p,"actor",()->true).outcome().state());assertEquals(before,fixture.operation());
        assertThrows(SecurityException.class,()->fixture.executor().closeExpired(p,"other",()->true));
        assertThrows(SecurityException.class,()->fixture.executor().closeExpired(p,"actor",()->false));assertEquals(before,fixture.operation());
        var altered=new ReviewRepairPreparation.Prepared(p.id(),"f".repeat(64),p.createdAt(),p.expiresAt());assertThrows(IllegalStateException.class,()->fixture.executor().closeExpired(altered,"actor",()->true));
    }
    @Test void malformedExpiredRecordAndNumericTypeSubstitutionStayUnknown() {
        var p=intent(System.currentTimeMillis()-1000);record(p,"EXECUTING");var records=fixture.mongo.getCollection(ReviewRepairJournal.COLLECTION);
        records.updateOne(new Document("_id",p.id()),new Document("$set",new Document("extra","unexpected")));var before=fixture.operation();
        assertEquals("UNKNOWN",fixture.executor().closeExpired(p,"actor",()->true).outcome().state());assertEquals(before,fixture.operation());
        records.updateOne(new Document("_id",p.id()),new Document("$unset",new Document("extra","")).append("$set",new Document("createdAt",(double)p.createdAt())));before=fixture.operation();
        assertEquals("UNKNOWN",fixture.executor().closeExpired(p,"actor",()->true).outcome().state());assertEquals(before,fixture.operation());
    }
    @ParameterizedTest @ValueSource(strings={"beforeSnapshot","afterVersionWrite"})
    void closureWinsAgainstAnOldTransactionAndPreventsItsVersionCommit(String phase)throws Exception {
        var p=intent(System.currentTimeMillis()+1000);var before=fixture.version();var reader=spy(fixture.reader);var closed=new AtomicBoolean();
        doAnswer(i->{
            var value=(RawReviewSnapshotReader.Captured)i.callRealMethod();
            boolean selected=phase.equals("beforeSnapshot") || !value.sha256().equals(p.sha256());
            if(selected && closed.compareAndSet(false,true)){expire(p);assertEquals("NOT_APPLIED",fixture.executor().closeExpired(p,"actor",()->true).outcome().state());}
            return value;
        }).when(reader).capture(any(ClientSession.class),eq("p"),eq(0),eq("v"));
        var outcome=new ReviewIsolationExecutor(fixture.mongo,fixture.archive,reader,fixture.journal).execute(p,"actor",()->true);
        assertTrue(closed.get());assertEquals("NOT_APPLIED",outcome.state());assertEquals(before,fixture.version());assertEquals("EXPIRED_CLAIM_CLOSED",fixture.operation().get("resolution",Document.class).get("kind"));
    }
    @Test void lateExecutorStartingAfterClosureCannotExecute()throws Exception {
        var p=intent(System.currentTimeMillis()+1000);var claim=fixture.journal.claim(p,"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW,()->true);assertNotNull(claim);expire(p);
        var before=fixture.version();assertEquals("NOT_APPLIED",fixture.executor().closeExpired(p,"actor",()->true).outcome().state());
        assertEquals("NOT_APPLIED",fixture.executor().execute(p,"actor",()->true).state());assertEquals(before,fixture.version());
    }
    @Test void lostClosureAcknowledgementRecoversWithoutASecondWrite() {
        var p=intent(System.currentTimeMillis()-1000);record(p,"UNKNOWN");var journal=spy(fixture.journal);var writes=new AtomicInteger();
        doAnswer(i->{i.callRealMethod();writes.incrementAndGet();throw new MongoException("lost acknowledgement");}).when(journal).closeExpired(eq(p),eq("actor"),any());
        var executor=new ReviewIsolationExecutor(fixture.mongo,fixture.archive,fixture.reader,journal);var before=fixture.version();
        assertEquals("NOT_APPLIED",executor.closeExpired(p,"actor",()->true).outcome().state());assertEquals(1,writes.get());assertEquals(before,fixture.version());
    }
    @Test void anAlreadyCommittedIsolationReceiptIsNeverOverwritten() {
        var p=fixture.prepare();assertEquals("APPLIED",fixture.executor().execute(p,"actor",()->true).state());var before=fixture.version();var operation=fixture.operation();
        assertEquals("APPLIED",fixture.executor().closeExpired(p,"actor",()->true).outcome().state());assertEquals(operation,fixture.operation());assertEquals(before,fixture.version());
    }
    @Test void committedTransactionWinsWhileClosureWaitsOnTheJournalWrite()throws Exception {
        var p=intent(System.currentTimeMillis()+1000);var sent=new CountDownLatch(1);
        String port=System.getenv().getOrDefault("WARDEN_REPAIR_TX_DB_PORT","27031");
        var settings=MongoClientSettings.builder().applyConnectionString(new ConnectionString("mongodb://127.0.0.1:"+port+"/?directConnection=true&serverSelectionTimeoutMS=3000&socketTimeoutMS=5000"))
                .addCommandListener(new com.mongodb.event.CommandListener(){@Override public void commandStarted(com.mongodb.event.CommandStartedEvent e){
                    if(e.getCommandName().equals("update") && e.getCommand().toJson().contains("EXPIRED_CLAIM_CLOSED"))sent.countDown();
                }}).build();
        try(var client=MongoClients.create(settings);var worker=Executors.newSingleThreadExecutor()) {
            var mongo=new org.springframework.data.mongodb.core.MongoTemplate(client,fixture.mongo.getDb().getName());
            var closer=new ReviewIsolationExecutor(mongo,fixture.archive,fixture.reader,new ReviewRepairJournal(mongo,fixture.archive));
            var wrapped=spy(fixture.mongo);var factory=spy(fixture.mongo.getMongoDatabaseFactory());doReturn(factory).when(wrapped).getMongoDatabaseFactory();
            var future=new AtomicReference<Future<ReviewIsolationExecutor.Receipt>>();
            doAnswer(i->{var session=spy((ClientSession)i.callRealMethod());doAnswer(c->{
                expire(p);future.set(worker.submit(()->closer.closeExpired(p,"actor",()->true)));
                assertTrue(sent.await(5,TimeUnit.SECONDS));return c.callRealMethod();
            }).when(session).commitTransaction();return session;}).when(factory).getSession(any(ClientSessionOptions.class));
            var result=new ReviewIsolationExecutor(wrapped,fixture.archive,fixture.reader,fixture.journal).execute(p,"actor",()->true);
            assertEquals("APPLIED",result.state());assertEquals("APPLIED",future.get().get(10,TimeUnit.SECONDS).outcome().state());
            assertNull(fixture.operation().get("resolution"));assertEquals(result.afterSha256(),fixture.reader.capture("p",0,"v").sha256());
        }
    }
    @Test void permissionLossAtTheFinalJournalCheckpointPreventsClosure() {
        var p=intent(System.currentTimeMillis()-1000);record(p,"UNKNOWN");var before=fixture.operation();var checks=new AtomicInteger();
        assertThrows(SecurityException.class,()->fixture.journal.closeExpired(p,"actor",()->checks.incrementAndGet()==1));assertEquals(before,fixture.operation());
    }
    @Test void standaloneIsRejectedBeforeClosingAClaim() {
        var p=intent(System.currentTimeMillis()-1000);record(p,"UNKNOWN");var before=fixture.operation();var mongo=spy(fixture.mongo);var db=spy(fixture.mongo.getDb());doReturn(db).when(mongo).getDb();
        doReturn(new Document("isWritablePrimary",true)).when(db).runCommand(eq(new Document("hello",1)),eq(ReadPreference.primary()));
        var executor=new ReviewIsolationExecutor(mongo,fixture.archive,fixture.reader,fixture.journal);
        assertThrows(IllegalStateException.class,()->executor.closeExpired(p,"actor",()->true));assertEquals(before,fixture.operation());
    }

}
