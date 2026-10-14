package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Actor-scoped discovery only; a recorded state never substitutes for an authenticated receipt. */
public final class ReviewRepairOperationReader {
    public record Item(String id,String recordedState) {}
    public record Page(List<Item> items,String nextCursor,String order) {}
    private static final String UUID="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    public static final String INDEX="review_repair_actor_action_id";
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private static final List<String> STATES=List.of("RESERVED","EXECUTING","UNKNOWN","APPLIED","NOT_APPLIED");
    private final MongoCollection<Document> operations;
    public ReviewRepairOperationReader(MongoTemplate mongo) {
        operations=mongo.getCollection(ReviewRepairJournal.COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY);
    }
    public void initialize() {
        operations.withWriteConcern(WriteConcern.MAJORITY.withJournal(true)).withTimeout(5000,TimeUnit.MILLISECONDS)
                .createIndex(new Document("actor",1).append("action",1).append("_id",1),new IndexOptions().name(INDEX).collation(BINARY));
    }
    public Page page(String actor,String cursor,int limit) {
        if(actor==null || actor.isBlank() || actor.length()>256 || limit<1 || limit>25)throw new IllegalArgumentException("Invalid operation page");
        String after=null;
        if(cursor!=null) {if(!cursor.matches("r1\\."+UUID))throw new IllegalArgumentException("Invalid operation cursor");after=cursor.substring(3);}
        var id=new Document("$type","string").append("$regex","^"+UUID+"$");if(after!=null)id.append("$gt",after);
        var scalar=new Document("$and",List.of(new Document("$eq",List.of(new Document("$type","$actor"),"string")),new Document("$eq",List.of(new Document("$type","$action"),"string"))));
        var query=new Document("actor",actor).append("action","ISOLATE_REVIEW").append("_id",id).append("$expr",scalar);
        var state=new Document("$cond",List.of(new Document("$in",List.of("$state",STATES)),"$state","UNKNOWN"));
        var pipeline=List.of(new Document("$match",query),new Document("$sort",new Document("_id",1)),new Document("$limit",limit+1),
                new Document("$project",new Document("_id",1).append("recordedState",state)));
        var records=ReviewRepairIo.collection(operations).aggregate(pipeline).hintString(INDEX).collation(BINARY).allowDiskUse(false)
                .maxTime(5,TimeUnit.SECONDS).batchSize(limit+1).into(new ArrayList<>());
        boolean more=records.size()>limit;var items=new ArrayList<Item>();
        for(var record:records.subList(0,Math.min(records.size(),limit))) {
            String operationId=record.getString("_id"),recordedState=record.getString("recordedState");
            if(operationId==null || !operationId.matches(UUID) || !STATES.contains(recordedState))throw new IllegalStateException("Invalid operation record");
            items.add(new Item(operationId,recordedState));
        }
        return new Page(List.copyOf(items),more?"r1."+items.getLast().id():null,"OPERATION_ID");
    }
}
