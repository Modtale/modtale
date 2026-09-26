package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Bounded discovery references; every selected observation still needs authenticated receipt recovery. */
public final class ReviewObservationReader {
    public record Item(String id,String recordedState) {}
    public record Page(List<Item> items,String nextCursor,String order) {}
    public static final String INDEX="review_observation_actor_isolation_id";
    private static final String UUID="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final List<String> STATES=List.of("RESERVED","READING","OBSERVED","UNKNOWN");
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private final MongoCollection<Document> observations;
    public ReviewObservationReader(MongoTemplate mongo) {
        observations=mongo.getCollection(ReviewCancellationReconciler.COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY).withTimeout(5000,TimeUnit.MILLISECONDS);
    }
    public void initialize() {
        observations.withWriteConcern(WriteConcern.MAJORITY.withJournal(true)).createIndex(new Document("intent.actor",1).append("intent.isolationId",1).append("_id",1),new IndexOptions().name(INDEX).collation(BINARY));
    }
    Page page(ReviewOrphanCancellationJournal.Prepared original,String actor,String cursor,int limit) {
        if(original==null || actor==null || actor.isBlank() || actor.length()>256 || limit<1 || limit>25)throw new IllegalArgumentException("Invalid observation page");
        String prefix="c1."+original.isolationId()+".";String after=null;
        if(cursor!=null){if(!cursor.startsWith(prefix) || !cursor.substring(prefix.length()).matches(UUID))throw new IllegalArgumentException("Invalid observation cursor");after=cursor.substring(prefix.length());}
        var id=new Document("$type","string").append("$regex","^"+UUID+"$");if(after!=null)id.append("$gt",after);
        var types=new ArrayList<Document>();
        for(String field:List.of("actor","isolationId","targetSha256"))types.add(new Document("$eq",List.of(new Document("$type","$intent."+field),"string")));
        for(String field:List.of("createdAt","expiresAt"))types.add(new Document("$eq",List.of(new Document("$type","$intent."+field),"long")));
        types.add(new Document("$eq",List.of(new Document("$type","$intent"),"object")));
        var query=new Document("intent.actor",actor).append("intent.isolationId",original.isolationId()).append("intent.targetSha256",original.targetSha256())
                .append("intent.createdAt",original.createdAt()).append("intent.expiresAt",original.expiresAt()).append("_id",id).append("$expr",new Document("$and",types));
        var state=new Document("$cond",List.of(new Document("$in",List.of("$state",STATES)),"$state","UNKNOWN"));
        var pipeline=List.of(new Document("$match",query),new Document("$sort",new Document("_id",1)),new Document("$limit",limit+1),new Document("$project",new Document("_id",1).append("recordedState",state)));
        var records=ReviewRepairIo.collection(observations).aggregate(pipeline).hintString(INDEX).collation(BINARY).allowDiskUse(false).maxTime(5,TimeUnit.SECONDS).batchSize(limit+1).into(new ArrayList<>());
        var items=new ArrayList<Item>();
        for(var record:records.subList(0,Math.min(limit,records.size()))) {
            String key=record.getString("_id"),recordedState=record.getString("recordedState");
            if(key==null || !key.matches(UUID) || !STATES.contains(recordedState))throw new IllegalStateException("Invalid observation reference");
            items.add(new Item(key,recordedState));
        }
        return new Page(List.copyOf(items),records.size()>limit?prefix+items.getLast().id():null,"OBSERVATION_ID");
    }
}
