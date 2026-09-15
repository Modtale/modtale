package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.event.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.net.*;
import java.io.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class ReviewRepairIoStallTest {
    MongoClient direct,client;MongoTemplate mongo;ReplyProxy proxy;ReviewSnapshotArchive archive;String database;
    AtomicBoolean stallInsert=new AtomicBoolean(),stallWrite=new AtomicBoolean();AtomicLong remaining=new AtomicLong(TimeUnit.SECONDS.toNanos(2));AtomicBoolean expireAfterInsert=new AtomicBoolean();
    @BeforeEach void setup(TestInfo info)throws Exception {
        boolean smallBuffers=info.getTestMethod().orElseThrow().getName().startsWith("blockedSocketWrite");
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException();
        direct=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000&socketTimeoutMS=5000");
        proxy=new ReplyProxy(Integer.parseInt(port),smallBuffers);database="warden_io_"+UUID.randomUUID().toString().replace("-","");
        client=MongoClients.create(MongoClientSettings.builder().applyConnectionString(new ConnectionString("mongodb://127.0.0.1:"+proxy.port()+"/?directConnection=true&serverSelectionTimeoutMS=3000&socketTimeoutMS=0"))
                .applyToSocketSettings(settings->settings.sendBufferSize(smallBuffers?1024:0))
                .addCommandListener(new CommandListener() {
                    @Override public void commandStarted(CommandStartedEvent event){if(event.getCommandName().equals("insert")) {if(stallInsert.get())proxy.paused.set(true);if(stallWrite.get())proxy.pausedRequests.set(true);}}
                    @Override public void commandSucceeded(CommandSucceededEvent event){if(event.getCommandName().equals("insert") && expireAfterInsert.get())remaining.set(0);}
                }).build());
        mongo=new MongoTemplate(client,database);mongo.getDb().withTimeout(3000,TimeUnit.MILLISECONDS).runCommand(new Document("ping",1));
        archive=new ReviewSnapshotArchive(mongo,"test",Map.of("test",new byte[32]));
    }
    @AfterEach void cleanup()throws Exception {
        if(proxy!=null)proxy.close();if(client!=null)client.close();
        if(direct!=null){direct.getDatabase(database).drop();direct.close();}
    }
    ReviewSnapshotArchive.Snapshot snapshot(int size){long now=System.currentTimeMillis();return new ReviewSnapshotArchive.Snapshot(UUID.randomUUID().toString(),"p",0,"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW,now,now+60000,new byte[size]);}

    @Test void stalledReadKeepsAdmissionUntilDriverActuallyReturns()throws Exception {
        var reader=new RawReviewSnapshotReader(mongo);var preparation=new ReviewRepairPreparation(archive,reader,Clock.systemUTC(),1,60000);var isolation=mock(ReviewIsolationExecutor.class);
        var workflow=new ReviewRepairWorkflow(preparation,isolation,1,1000,System::nanoTime);
        var request=new ReviewRepairPreparation.Request(UUID.randomUUID().toString(),"p",0,"v","a".repeat(64),"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW);
        proxy.paused.set(true);
        try(var worker=Executors.newSingleThreadExecutor()) {
            var pending=worker.submit(()->workflow.prepare(request,()->true));
            try {
                assertTrue(proxy.withheld.await(5,TimeUnit.SECONDS));assertFalse(pending.isDone());
                assertEquals(new ReviewRepairWorkflow.Status("OPEN",1),workflow.status());
                assertThrows(IllegalStateException.class,()->workflow.isolate(new ReviewRepairPreparation.Prepared(request.id(),request.expectedSha256(),1,2),"actor",()->true));
                workflow.close();assertEquals(new ReviewRepairWorkflow.Status("DRAINING",1),workflow.status());
                var failure=assertThrows(ExecutionException.class,()->pending.get(10,TimeUnit.SECONDS));assertInstanceOf(MongoOperationTimeoutException.class,failure.getCause());
                assertEquals(new ReviewRepairWorkflow.Status("CLOSED",0),workflow.status());verifyNoInteractions(isolation);
            } finally {proxy.close();workflow.close();}
        }
        assertEquals(0,direct.getDatabase(database).getCollection(ReviewSnapshotArchive.METADATA).countDocuments());
    }

    @Test void lostInsertReplyLeavesOnlyUnpublishedChunkAndNeverClaimsRetention() {
        var snapshot=snapshot(5);stallInsert.set(true);long started=System.nanoTime();
        try(var io=ReviewRepairIo.open(()->TimeUnit.SECONDS.toNanos(1)-(System.nanoTime()-started))) {
            assertThrows(RuntimeException.class,()->archive.retain(snapshot));
        }
        assertTrue(proxy.paused.get());assertEquals(1,direct.getDatabase(database).getCollection(ReviewSnapshotArchive.CHUNKS).countDocuments());
        assertEquals(0,direct.getDatabase(database).getCollection(ReviewSnapshotArchive.METADATA).countDocuments());
        assertTrue(System.nanoTime()-started<TimeUnit.SECONDS.toNanos(10));
    }

    @Test void exhaustedBudgetStopsBetweenArchiveChunks() {
        var snapshot=snapshot(ReviewSnapshotArchive.CHUNK_BYTES+1);expireAfterInsert.set(true);
        try(var io=ReviewRepairIo.open(remaining::get)){assertThrows(IllegalStateException.class,()->archive.retain(snapshot));}
        assertEquals(0,remaining.get());assertEquals(1,direct.getDatabase(database).getCollection(ReviewSnapshotArchive.CHUNKS).countDocuments());
        assertEquals(0,direct.getDatabase(database).getCollection(ReviewSnapshotArchive.METADATA).countDocuments());
    }


    @Test void blockedSocketWriteRetainsAdmissionBeyondCheckpointDeadlineUntilTransportCloses()throws Exception {
        var directMongo=new MongoTemplate(direct,database);
        directMongo.getCollection("projects").insertOne(new Document("_id","p").append("versions",List.of(new Document("_id","v").append("padding",new org.bson.types.Binary(new byte[300000])))));
        var expected=new RawReviewSnapshotReader(directMongo).capture("p",0,"v");var reader=new RawReviewSnapshotReader(mongo);
        var preparation=new ReviewRepairPreparation(archive,reader,Clock.systemUTC(),1,60000);var isolation=mock(ReviewIsolationExecutor.class);
        var workflow=new ReviewRepairWorkflow(preparation,isolation,1,1000,System::nanoTime);
        var request=new ReviewRepairPreparation.Request(UUID.randomUUID().toString(),"p",0,"v",expected.sha256(),"actor",ReviewSnapshotArchive.Action.ISOLATE_REVIEW);
        stallWrite.set(true);
        try(var worker=Executors.newSingleThreadExecutor()) {
            var pending=worker.submit(()->workflow.prepare(request,()->true));
            try {
                assertTrue(proxy.requestWithheld.await(5,TimeUnit.SECONDS));
                // The pinned synchronous driver does not interrupt a blocked socket write at timeoutMS.
                assertThrows(TimeoutException.class,()->pending.get(1500,TimeUnit.MILLISECONDS));
                assertEquals(new ReviewRepairWorkflow.Status("OPEN",1,1),workflow.status());workflow.close();
                assertEquals(new ReviewRepairWorkflow.Status("DRAINING",1,1),workflow.status());
                proxy.close();assertThrows(ExecutionException.class,()->pending.get(10,TimeUnit.SECONDS));
                assertEquals(new ReviewRepairWorkflow.Status("CLOSED",0),workflow.status());verifyNoInteractions(isolation);
            } finally {proxy.close();workflow.close();}
        }
        assertEquals(0,direct.getDatabase(database).getCollection(ReviewSnapshotArchive.METADATA).countDocuments());
    }

    /** Transparent loopback proxy that can retain replies while requests still reach the real database. */
    static final class ReplyProxy implements AutoCloseable {
        final ServerSocket listener;final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();final List<Socket> sockets=new CopyOnWriteArrayList<>();
        final AtomicBoolean paused=new AtomicBoolean(),pausedRequests=new AtomicBoolean(),closed=new AtomicBoolean();final CountDownLatch withheld=new CountDownLatch(1),requestWithheld=new CountDownLatch(1),release=new CountDownLatch(1);
        ReplyProxy(int target)throws IOException {this(target,false);}
        ReplyProxy(int target,boolean smallBuffers)throws IOException {
            listener=new ServerSocket(0,16,InetAddress.getLoopbackAddress());
            workers.submit(()->{while(!closed.get())try {
                Socket front=listener.accept();if(smallBuffers)front.setReceiveBufferSize(1024);sockets.add(front);Socket back=new Socket("127.0.0.1",target);sockets.add(back);
                workers.submit(()->copy(front,back,false));workers.submit(()->copy(back,front,true));
            }catch(IOException failure){if(!closed.get())throw new UncheckedIOException(failure);}});
        }
        int port(){return listener.getLocalPort();}
        void copy(Socket source,Socket target,boolean reply) {
            try {byte[] buffer=new byte[8192];int count;
                while((count=source.getInputStream().read(buffer))!=-1) {
                    if(reply && paused.get()){withheld.countDown();release.await();}
                    if(!reply && pausedRequests.get()){requestWithheld.countDown();release.await();}
                    if(closed.get())return;target.getOutputStream().write(buffer,0,count);target.getOutputStream().flush();
                }
            }catch(IOException ignored){}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
        }
        @Override public void close() {
            if(!closed.compareAndSet(false,true))return;release.countDown();try{listener.close();}catch(IOException ignored){}
            for(var socket:sockets)try{socket.close();}catch(IOException ignored){}
            workers.shutdownNow();
            try{if(!workers.awaitTermination(5,TimeUnit.SECONDS))throw new IllegalStateException("Proxy did not stop");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
        }
    }
}
