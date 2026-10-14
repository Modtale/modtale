package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Bounded discovery only. A reference is not an authenticated receipt or permission to dispatch. */
public final class ReviewReplacementAdmissionReader {
    public static final String HELD_INDEX="review_replacement_state_id", REQUEST_INDEX="review_replacement_request";
    private static final String UUID="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    public record Page(List<String> operationIds,String nextCursor) {}
    private final MongoCollection<Document> admissions;
    public ReviewReplacementAdmissionReader(MongoTemplate mongo) {
        admissions=mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS).withReadPreference(ReadPreference.primary())
                .withReadConcern(ReadConcern.MAJORITY).withTimeout(5000,TimeUnit.MILLISECONDS);
    }
    public void initialize() {
        var writes=admissions.withWriteConcern(WriteConcern.MAJORITY.withJournal(true));
        writes.createIndex(new Document("state",1).append("_id",1),new IndexOptions().name(HELD_INDEX).collation(BINARY));
        writes.createIndex(new Document("requestId",1),new IndexOptions().name(REQUEST_INDEX).collation(BINARY));
    }
    public Page held(String cursor,int limit,BooleanSupplier permitted) {
        permission(permitted);
        if(limit<1 || limit>64 || cursor!=null && !cursor.matches("r1\\."+UUID))throw new IllegalArgumentException("Invalid admission page");
        var id=new Document("$type","string").append("$regex","^"+UUID+"$");if(cursor!=null)id.append("$gt",cursor.substring(3));
        var query=new Document("state","HELD").append("_id",id)
                .append("$expr",new Document("$eq",List.of(new Document("$type","$state"),"string")));
        var records=ReviewRepairIo.collection(admissions).find(query).hintString(HELD_INDEX).collation(BINARY)
                .projection(new Document("_id",1)).sort(new Document("_id",1)).limit(limit+1).batchSize(limit+1)
                .maxTime(5,TimeUnit.SECONDS).into(new ArrayList<>());
        permission(permitted);
        var ids=records.stream().limit(limit).map(record->record.getString("_id")).toList();
        return new Page(ids,records.size()>limit?"r1."+ids.getLast():null);
    }
    /** Any matching durable reference, including malformed records, prevents treating the request as untracked. */
    public boolean hasRequest(String requestId,BooleanSupplier permitted) {
        permission(permitted);
        if(requestId==null || !requestId.matches(UUID))throw new IllegalArgumentException("Invalid admission request identity");
        var found=ReviewRepairIo.collection(admissions).find(new Document("requestId",requestId)).hintString(REQUEST_INDEX)
                .collation(BINARY).projection(new Document("_id",1)).limit(1).maxTime(5,TimeUnit.SECONDS).first();
        permission(permitted);return found!=null;
    }
    private static void permission(BooleanSupplier permitted) {
        if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Replacement admission access is not permitted");
    }
}
