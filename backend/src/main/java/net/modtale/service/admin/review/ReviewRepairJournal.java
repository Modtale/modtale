package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** One-shot execution claims. An uncertain claim is never automatically reclaimed. */
public final class ReviewRepairJournal {
    public static final String COLLECTION="review_repair_operations";
    public record Claim(String id,String token) {
        public Claim {if(!uuid(id)||!uuid(token))throw new IllegalArgumentException("Invalid repair claim");}
    }
    private final MongoCollection<Document> operations;
    private final ReviewSnapshotArchive archive;
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    public ReviewRepairJournal(MongoTemplate mongo,ReviewSnapshotArchive archive) {
        this.archive=Objects.requireNonNull(archive);operations=mongo.getCollection(COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY)
                .withWriteConcern(WriteConcern.MAJORITY.withJournal(true).withWTimeout(10,TimeUnit.SECONDS));
    }
    public Claim claim(ReviewRepairPreparation.Prepared prepared,String actor,ReviewSnapshotArchive.Action action,BooleanSupplier permitted) {
        requirePermission(permitted);Objects.requireNonNull(prepared);
        var snapshot=archive.load(prepared.id());
        if(!snapshot.actorId().equals(actor)||snapshot.action()!=action||snapshot.createdAt()!=prepared.createdAt()||snapshot.expiresAt()!=prepared.expiresAt()
                ||!digest(snapshot.versionBytes()).equals(prepared.sha256()))throw unavailable();
        var record=new Document("_id",prepared.id()).append("token",UUID.randomUUID().toString()).append("sha256",prepared.sha256())
                .append("actor",actor).append("action",action.name()).append("createdAt",prepared.createdAt()).append("expiresAt",prepared.expiresAt()).append("state","RESERVED");
        requirePermission(permitted);
        try {operations.insertOne(record);}
        catch(MongoException uncertain) {
            var existing=read(prepared.id());
            if(existing==null)throw unavailable();
            if(!record.equals(existing))return null;
        }
        requirePermission(permitted);
        var query=new Document(record).append("$expr",new Document("$and",List.of(new Document("$gte",List.of("$$NOW",new Date(prepared.createdAt()))),
                new Document("$lt",List.of("$$NOW",new Date(prepared.expiresAt()))))));
        try {
            var armed=operations.findOneAndUpdate(query,List.of(new Document("$set",new Document("state","EXECUTING").append("startedAt","$$NOW"))),
                    new FindOneAndUpdateOptions().collation(BINARY).returnDocument(ReturnDocument.AFTER));
            requirePermission(permitted);
            return armed!=null && executing(armed,record)?new Claim(prepared.id(),record.getString("token")):null;
        } catch(MongoException uncertain) {
            requirePermission(permitted);var armed=read(prepared.id());requirePermission(permitted);
            if(armed!=null && executing(armed,record))return new Claim(prepared.id(),record.getString("token"));
            throw unavailable();
        }
    }
    public boolean markUnknown(Claim claim,BooleanSupplier permitted) {
        requirePermission(permitted);var stored=read(claim.id());if(stored==null || !claim.token().equals(stored.get("token")))return false;
        if("UNKNOWN".equals(stored.get("state")))return true;
        if(!"EXECUTING".equals(stored.get("state")) || stored.size()!=9 || !(stored.get("startedAt") instanceof Date))return false;
        requirePermission(permitted);
        try {
            return operations.updateOne(stored,List.of(new Document("$set",new Document("state","UNKNOWN").append("uncertainAt","$$NOW"))),new UpdateOptions().collation(BINARY)).getModifiedCount()==1;
        } catch(MongoException uncertain) {
            var current=read(claim.id());return current!=null && claim.token().equals(current.get("token")) && "UNKNOWN".equals(current.get("state"));
        }
    }
    private static boolean executing(Document stored,Document reserved) {
        var expected=new Document(reserved);expected.put("state","EXECUTING");
        if(stored.size()!=9 || !(stored.get("startedAt") instanceof Date))return false;
        var copy=new Document(stored);copy.remove("startedAt");return expected.equals(copy);
    }
    private Document read(String id){return operations.find(new Document("_id",id)).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();}
    private static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception invalid){throw unavailable();}}
    private static boolean uuid(String id){return id!=null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    private static void requirePermission(BooleanSupplier permitted){if(permitted==null||!permitted.getAsBoolean())throw new SecurityException("Repair execution is not permitted");}
    private static IllegalStateException unavailable(){return new IllegalStateException("Repair execution claim is unavailable or inconsistent");}
}
