package net.modtale.service.admin.review;

import com.mongodb.client.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_REVIEW_DB_TEST",matches="true")
class ReviewRepairOperationReaderTest {
    MongoClient client;MongoTemplate mongo;MongoCollection<Document> operations;ReviewRepairOperationReader reader;
    @BeforeEach void setup() {
        String port=System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT","27030");if(!Set.of("27029","27030").contains(port))throw new IllegalArgumentException();
        client=MongoClients.create("mongodb://127.0.0.1:"+port+"/?serverSelectionTimeoutMS=3000&socketTimeoutMS=5000");mongo=new MongoTemplate(client,"warden_operation_page_"+UUID.randomUUID().toString().replace("-",""));
        mongo.getDb().createCollection(ReviewRepairJournal.COLLECTION,new com.mongodb.client.model.CreateCollectionOptions().collation(com.mongodb.client.model.Collation.builder().locale("en").collationStrength(com.mongodb.client.model.CollationStrength.SECONDARY).build()));
        operations=mongo.getCollection(ReviewRepairJournal.COLLECTION);reader=new ReviewRepairOperationReader(mongo);reader.initialize();
    }
    @AfterEach void cleanup(){mongo.getDb().drop();client.close();}
    String id(int n){return String.format("%08x-0000-0000-0000-%012x",n,n);}
    Document record(int n,Object actor,Object action,Object state){return new Document("_id",id(n)).append("actor",actor).append("action",action).append("state",state);}
    @Test void discoveryIsActorScopedBinaryAndScalar() {
        operations.insertMany(List.of(record(1,"actor","ISOLATE_REVIEW","EXECUTING"),record(2,"Actor","ISOLATE_REVIEW","APPLIED"),record(3,List.of("actor"),"ISOLATE_REVIEW","APPLIED"),record(4,"actor",List.of("ISOLATE_REVIEW"),"APPLIED"),record(5,"actor","REPLACE_REVIEW","APPLIED")));
        var page=reader.page("actor",null,25);assertEquals(List.of(new ReviewRepairOperationReader.Item(id(1),"EXECUTING")),page.items());assertNull(page.nextCursor());assertEquals("OPERATION_ID",page.order());
    }
    @Test void pagesStayBoundedAndDeletedCursorsStillAdvance() {
        for(int n=1;n<=4;n++)operations.insertOne(record(n,"actor","ISOLATE_REVIEW","UNKNOWN"));
        var first=reader.page("actor",null,2);assertEquals(2,first.items().size());assertEquals("r1."+id(2),first.nextCursor());
        operations.deleteOne(new Document("_id",id(2)));var second=reader.page("actor",first.nextCursor(),2);assertEquals(List.of(id(3),id(4)),second.items().stream().map(ReviewRepairOperationReader.Item::id).toList());assertNull(second.nextCursor());
        assertTrue(reader.page("other",first.nextCursor(),2).items().isEmpty());
    }
    @Test void malformedStateAndPrivatePayloadsNeverExpandTheResponse() {
        var large="private".repeat(100000);operations.insertOne(record(1,"actor","ISOLATE_REVIEW",large).append("payload",large).append("token",large));
        var before=operations.find().first();var page=reader.page("actor",null,1);assertEquals(List.of(new ReviewRepairOperationReader.Item(id(1),"UNKNOWN")),page.items());assertEquals(before,operations.find().first());assertFalse(page.toString().contains("private"));
    }
    @Test void malformedIdentityDoesNotEnterTheReferenceStream() {
        operations.insertOne(new Document("_id","INVALID").append("actor","actor").append("action","ISOLATE_REVIEW"));
        operations.insertOne(record(1,"actor","ISOLATE_REVIEW","APPLIED"));assertEquals(1,reader.page("actor",null,25).items().size());
    }
    @Test void invalidCursorsAndLimitsAreRejected() {
        for(String cursor:List.of("",id(1),"r2."+id(1),"r1."+id(1)+"extra"))assertThrows(IllegalArgumentException.class,()->reader.page("actor",cursor,1));
        assertThrows(IllegalArgumentException.class,()->reader.page("actor",null,0));assertThrows(IllegalArgumentException.class,()->reader.page("actor",null,26));
    }
    @Test void missingRequiredIndexDoesNotFallBackToACollectionScan() {
        operations.insertOne(record(1,"actor","ISOLATE_REVIEW","UNKNOWN"));operations.dropIndex(ReviewRepairOperationReader.INDEX);
        assertThrows(com.mongodb.MongoException.class,()->reader.page("actor",null,25));
    }
}
