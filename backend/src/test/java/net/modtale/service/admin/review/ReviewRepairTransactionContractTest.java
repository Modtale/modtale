package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REPAIR_TX_DB_TEST",matches="true")
class ReviewRepairTransactionContractTest {
    MongoClient client;MongoTemplate mongo;MongoCollection<Document> projects,operations;RawReviewSnapshotReader reader;
    TransactionOptions options=TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY.withJournal(true))
            .readPreference(ReadPreference.primary()).maxCommitTime(5000L,TimeUnit.MILLISECONDS).build();
    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REPAIR_TX_DB_PORT","27031");if(!Set.of("27031","27032").contains(port))throw new IllegalArgumentException();
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?directConnection=true&serverSelectionTimeoutMS=3000&socketTimeoutMS=10000");
        mongo=new MongoTemplate(client,"warden_repair_tx_"+UUID.randomUUID().toString().replace("-",""));
        projects=mongo.getCollection("projects");operations=mongo.getCollection("review_repair_operations");
        projects.insertOne(new Document("_id","p").append("versions",List.of(new Document("_id","v").append("scanResult",new Document("status","SCANNING").append("scanAttempt",1)))));
        operations.insertOne(new Document("_id","operation").append("state","EXECUTING"));reader=new RawReviewSnapshotReader(mongo);
    }
    @AfterEach void cleanup(){mongo.getDb().drop();client.close();}
    Document storedVersion(){return projects.find().first().getList("versions",Document.class).getFirst();}
    void stage(ClientSession session) {
        assertEquals(1,projects.updateOne(session,new Document("_id","p"),new Document("$set",new Document("versions.0.scanResult.status","FAILED"))).getModifiedCount());
        assertEquals(1,operations.updateOne(session,new Document("_id","operation"),new Document("$set",new Document("state","APPLIED"))).getModifiedCount());
    }
    @Test void ordinaryEmbeddedEqualityDoesNotProveRawByteIdentity() {
        var original=storedVersion();var before=reader.capture("p",0,"v");
        projects.updateOne(new Document("_id","p"),new Document("$set",new Document("versions.0.scanResult.scanAttempt",1L)));
        assertInstanceOf(Long.class,storedVersion().get("scanResult",Document.class).get("scanAttempt"));
        assertNotEquals(before.sha256(),reader.capture("p",0,"v").sha256());
        assertNotNull(projects.find(new Document("_id","p").append("versions",original)).first());
    }
    @Test void concurrentTypeChangeAfterTransactionalCaptureCausesWriteConflict() {
        var original=reader.capture("p",0,"v");
        try(var session=client.startSession()) {
            session.startTransaction(options);assertEquals(original.sha256(),reader.capture(session,"p",0,"v").sha256());
            projects.updateOne(new Document("_id","p"),new Document("$set",new Document("versions.0.scanResult.scanAttempt",1L)));
            assertEquals(original.sha256(),reader.capture(session,"p",0,"v").sha256());
            var conflict=assertThrows(MongoException.class,()->stage(session));assertTrue(conflict.hasErrorLabel("TransientTransactionError"));session.abortTransaction();
        }
        assertEquals("SCANNING",storedVersion().get("scanResult",Document.class).getString("status"));assertEquals("EXECUTING",operations.find().first().getString("state"));
    }
    @Test void abortLeavesVersionAndOutcomeUnchanged() {
        try(var session=client.startSession()) {session.startTransaction(options);reader.capture(session,"p",0,"v");stage(session);session.abortTransaction();}
        assertEquals("SCANNING",storedVersion().get("scanResult",Document.class).getString("status"));assertEquals("EXECUTING",operations.find().first().getString("state"));
    }
    @Test void committedVersionAndOutcomeBecomeVisibleTogether() {
        try(var session=client.startSession()) {session.startTransaction(options);reader.capture(session,"p",0,"v");stage(session);session.commitTransaction();}
        assertEquals("FAILED",storedVersion().get("scanResult",Document.class).getString("status"));assertEquals("APPLIED",operations.find().first().getString("state"));
    }
    @Test void lostCommitAcknowledgementCanBeReconciledWithoutRepeatingWrites() {
        try(var real=client.startSession()) {
            var session=spy(real);session.startTransaction(options);reader.capture(session,"p",0,"v");stage(session);
            doAnswer(i->{i.callRealMethod();var lost=new MongoException("lost commit acknowledgement");lost.addLabel("UnknownTransactionCommitResult");throw lost;}).when(session).commitTransaction();
            var failure=assertThrows(MongoException.class,session::commitTransaction);assertTrue(failure.hasErrorLabel("UnknownTransactionCommitResult"));
        }
        assertEquals("APPLIED",operations.withReadConcern(ReadConcern.MAJORITY).find().first().getString("state"));
        assertEquals("FAILED",storedVersion().get("scanResult",Document.class).getString("status"));
    }
}
