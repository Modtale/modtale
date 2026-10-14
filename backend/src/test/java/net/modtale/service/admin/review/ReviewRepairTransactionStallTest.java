package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.event.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewRepairTransactionStallTest {
    MongoClient direct,client;MongoTemplate mongo;ReviewRepairIoStallTest.ReplyProxy proxy;String database;
    ReviewSnapshotArchive archive;RawReviewSnapshotReader reader;ReviewRepairJournal journal;ReviewRepairPreparation preparation;
    AtomicReference<String> stallCommand=new AtomicReference<>();CountDownLatch commandEntered=new CountDownLatch(1);
    @BeforeEach void setup()throws Exception {
        String port=System.getenv().getOrDefault("WARDEN_REPAIR_TX_DB_PORT","27031");if(!Set.of("27031","27032").contains(port))throw new IllegalArgumentException();
        direct=MongoClients.create("mongodb://127.0.0.1:"+port+"/?directConnection=true&serverSelectionTimeoutMS=3000&socketTimeoutMS=5000");
        proxy=new ReviewRepairIoStallTest.ReplyProxy(Integer.parseInt(port));database="warden_tx_stall_"+UUID.randomUUID().toString().replace("-","");
        client=MongoClients.create(MongoClientSettings.builder().applyConnectionString(new ConnectionString("mongodb://127.0.0.1:"+proxy.port()+"/?directConnection=true&serverSelectionTimeoutMS=3000&socketTimeoutMS=0"))
                .addCommandListener(new CommandListener() {
                    @Override public void commandStarted(CommandStartedEvent event) {
                        if(event.getCommandName().equals(stallCommand.get())){proxy.paused.set(true);commandEntered.countDown();}
                    }
                }).build());
        mongo=new MongoTemplate(client,database);mongo.getDb().withTimeout(3000,TimeUnit.MILLISECONDS).runCommand(new Document("ping",1));
        direct.getDatabase(database).getCollection("projects").insertOne(new Document("_id","p").append("versions",List.of(new Document("_id","v").append("reviewStatus","PENDING")
                .append("scanResult",new Document("status","SCANNING").append("scanState","REMOTE_REVIEW").append("verdict","BLOCK")))));
        archive=new ReviewSnapshotArchive(mongo,"test",Map.of("test",new byte[32]));reader=new RawReviewSnapshotReader(mongo);journal=new ReviewRepairJournal(mongo,archive);
        preparation=new ReviewRepairPreparation(archive,reader,Clock.systemUTC(),1,60000);
    }
    @AfterEach void cleanup() {
        if(proxy!=null)proxy.close();if(client!=null)client.close();if(direct!=null){direct.getDatabase(database).drop();direct.close();}
    }
    ReviewRepairWorkflow workflow(RawReviewSnapshotReader selected) {
        return new ReviewRepairWorkflow(preparation,new ReviewIsolationExecutor(mongo,archive,selected,journal),1,1000,System::nanoTime);
    }
    ReviewRepairPreparation.Prepared prepare() {
        return preparation.prepare(new ReviewRepairPreparation.Request(UUID.randomUUID().toString(),"p",0,"v",reader.capture("p",0,"v").sha256(),"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW),()->true);
    }
    Document project(){return direct.getDatabase(database).getCollection("projects").withReadConcern(ReadConcern.MAJORITY).find().first();}
    Document operation(){return direct.getDatabase(database).getCollection(ReviewRepairJournal.COLLECTION).withReadConcern(ReadConcern.MAJORITY).find().first();}
    void resume(){stallCommand.set(null);proxy.paused.set(false);proxy.release.countDown();mongo.getDb().withTimeout(3000,TimeUnit.MILLISECONDS).runCommand(new Document("ping",1));}

    @Test void actualLostCommitReplyKeepsCommittedReceiptAndNeverRepeatsVersionWrite()throws Exception {
        var prepared=prepare();stallCommand.set("commitTransaction");var workflow=workflow(reader);
        try(var worker=Executors.newSingleThreadExecutor()) {
            var running=worker.submit(()->workflow.isolate(prepared,"actor",()->true));
            try {
                assertTrue(commandEntered.await(5,TimeUnit.SECONDS));assertTrue(proxy.withheld.await(5,TimeUnit.SECONDS));assertFalse(running.isDone());
                assertEquals("APPLIED",operation().get("state"));var committed=project();
                workflow.close();assertEquals(new ReviewRepairWorkflow.Status("DRAINING",1),workflow.status());
                assertEquals("UNKNOWN",running.get(15,TimeUnit.SECONDS).state());assertEquals(new ReviewRepairWorkflow.Status("CLOSED",0),workflow.status());
                assertEquals("APPLIED",operation().get("state"));assertEquals(committed,project());
                resume();try(var restarted=workflow(reader)) {
                    var recovered=restarted.isolate(prepared,"actor",()->true);assertEquals("APPLIED",recovered.state());
                    assertEquals(operation().get("afterSha256"),recovered.afterSha256());assertEquals(committed,project());
                }
            } finally {proxy.close();workflow.close();}
        }
    }

    @Test void lostAbortReplyCannotTurnPermissionLossIntoAppliedRepair()throws Exception {
        var prepared=prepare();var before=project();var permitted=new AtomicBoolean(true);var selected=spy(reader);
        doAnswer(i->{var captured=(RawReviewSnapshotReader.Captured)i.callRealMethod();if(!captured.sha256().equals(prepared.sha256()))permitted.set(false);return captured;})
                .when(selected).capture(any(ClientSession.class),eq("p"),eq(0),eq("v"));
        var workflow=workflow(selected);stallCommand.set("abortTransaction");
        try(var worker=Executors.newSingleThreadExecutor()) {
            var running=worker.submit(()->workflow.isolate(prepared,"actor",permitted::get));
            try {
                assertTrue(commandEntered.await(5,TimeUnit.SECONDS));assertTrue(proxy.withheld.await(5,TimeUnit.SECONDS));assertFalse(running.isDone());
                workflow.close();assertEquals(new ReviewRepairWorkflow.Status("DRAINING",1),workflow.status());
                var failure=assertThrows(ExecutionException.class,()->running.get(15,TimeUnit.SECONDS));assertInstanceOf(SecurityException.class,failure.getCause());
                assertEquals(before,project());assertEquals("EXECUTING",operation().get("state"));assertEquals(new ReviewRepairWorkflow.Status("CLOSED",0),workflow.status());
                resume();try(var restarted=workflow(reader)) {assertEquals("UNKNOWN",restarted.isolate(prepared,"actor",()->true).state());assertEquals(before,project());}
            } finally {proxy.close();workflow.close();}
        }
    }
}
