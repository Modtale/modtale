package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.model.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.bson.types.Binary;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Atomic admission only; the durable remote scheduler delivers the fixed request. */
public final class ProjectMutationActivator {
    public static final String ADMISSIONS="project_mutation_admissions";
    public record Result(String state,String afterSha256) {}
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private static final TransactionOptions OPTIONS=TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT).readPreference(ReadPreference.primary())
            .writeConcern(WriteConcern.MAJORITY.withJournal(true)).maxCommitTime(5000L,TimeUnit.MILLISECONDS).build();
    private final MongoTemplate mongo;private final ReviewRepairWorkflow budget;private final ProjectMutationAdmissionPreparation preparation;
    private final ReviewSnapshotArchive archive;private final ReviewRepairJournal journal;private final RawReviewSnapshotReader reader;
    public ProjectMutationActivator(MongoTemplate mongo,ReviewRepairWorkflow budget,ProjectMutationAdmissionPreparation preparation,ReviewSnapshotArchive archive,ReviewRepairJournal journal) {
        this.mongo=Objects.requireNonNull(mongo);this.budget=Objects.requireNonNull(budget);this.preparation=Objects.requireNonNull(preparation);
        this.archive=Objects.requireNonNull(archive);this.journal=Objects.requireNonNull(journal);reader=new RawReviewSnapshotReader(mongo);
    }
    public Result activate(ProjectMutationAdmissionPreparation.Prepared prepared,String actor,BooleanSupplier permitted) {
        return budget.call(allowed->activateWithinBudget(prepared,actor,allowed),permitted);
    }
    private Result activateWithinBudget(ProjectMutationAdmissionPreparation.Prepared prepared,String actor,BooleanSupplier allowed) {
        var previous=receiptWithinBudget(prepared,actor,allowed);if(!"UNKNOWN".equals(previous.state()))return previous;
        preparation.verifyCurrent(prepared,actor,allowed);var source=archive.load(prepared.id());var projected=projected(source);String afterSha=digest(bytes(projected));
        var hello=ReviewRepairIo.database(mongo.getDb()).runCommand(new Document("hello",1),ReadPreference.primary());
        if(!(hello.get("setName") instanceof String) && !"isdbgrid".equals(hello.get("msg")))throw invalid();
        var claim=journal.claim(new ReviewRepairPreparation.Prepared(source.id(),prepared.decisionSha256(),source.createdAt(),source.expiresAt()),actor,source.action(),allowed);
        if(claim==null)return receiptWithinBudget(prepared,actor,allowed);boolean commitAttempted=false;
        try(var session=mongo.getMongoDatabaseFactory().getSession(ReviewRepairIo.sessionOptions())) {
            try {
                session.startTransaction(ReviewRepairIo.transactionOptions(OPTIONS));permission(allowed);
                var operations=ReviewRepairIo.collection(mongo.getCollection(ReviewRepairJournal.COLLECTION),session);
                var op=operations.find(session,new Document("_id",claim.id()).append("token",claim.token()).append("state","EXECUTING")).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();if(op==null)throw invalid();
                var current=reader.capture(session,source.projectId(),source.versionIndex(),prepared.versionId());
                String state="NOT_APPLIED",after=null;
                if(prepared.heldSha256().equals(current.sha256())) {
                    var query=new Document("_id",source.projectId()).append("status",new Document("$in",List.of("PENDING","PUBLISHED","UNLISTED","PRIVATE")))
                            .append("$expr",new Document("$and",List.of(new Document("$eq",List.of(new Document("$type","$status"),"string")),
                                    new Document("$gte",List.of("$$NOW",new Date(source.createdAt()))),new Document("$lt",List.of("$$NOW",new Date(source.expiresAt()))))));
                    permission(allowed);
                    if(ReviewRepairIo.collection(mongo.getCollection("projects"),session).updateOne(session,query,new Document("$set",new Document("versions."+source.versionIndex(),projected)),new UpdateOptions().collation(BINARY)).getModifiedCount()==1) {
                        var actual=reader.capture(session,source.projectId(),source.versionIndex(),prepared.versionId());if(!afterSha.equals(actual.sha256()))throw invalid();
                        // One retained admission per fixed request also fences competing signed decisions.
                        ReviewRepairIo.collection(mongo.getCollection(ADMISSIONS),session).insertOne(session,admission(source,prepared,afterSha));
                        state="APPLIED";after=afterSha;
                    }
                }
                permission(allowed);
                var fields=new Document("state",state).append("afterSha256",after).append("mutationId",prepared.mutationId()).append("requestId",prepared.binding().requestId()).append("finishedAt","$$NOW");
                if(operations.updateOne(session,op,List.of(new Document("$set",fields)),new UpdateOptions().collation(BINARY)).getModifiedCount()!=1)throw invalid();
                permission(allowed);commitAttempted=true;session.commitTransaction();return new Result(state,after);
            }catch(RuntimeException failure){if(!commitAttempted)try{session.abortTransaction();}catch(RuntimeException ignored){}throw failure;}
        }catch(RuntimeException uncertain) {
            try(var cleanup=ReviewRepairIo.cleanup()) {
                if(uncertain instanceof SecurityException){try{journal.markUnknown(claim,()->true);}catch(RuntimeException ignored){}throw uncertain;}
                try{var result=receiptWithinBudget(prepared,actor,allowed);if(!"UNKNOWN".equals(result.state()))return result;}catch(RuntimeException ignored){}
                try{journal.markUnknown(claim,()->true);}catch(RuntimeException ignored){}return new Result("UNKNOWN",null);
            }
        }
    }
    public Result receipt(ProjectMutationAdmissionPreparation.Prepared prepared,String actor,BooleanSupplier permitted) {
        return budget.call(allowed->receiptWithinBudget(prepared,actor,allowed),permitted);
    }
    Result receiptWithinBudget(ProjectMutationAdmissionPreparation.Prepared prepared,String actor,BooleanSupplier allowed) {
        permission(allowed);if(!prepared.equals(preparation.recoverWithinBudget(prepared.id(),actor,allowed)))throw invalid();var source=archive.load(prepared.id());
        var op=ReviewRepairIo.collection(mongo.getCollection(ReviewRepairJournal.COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY))
                .find(new Document("_id",prepared.id())).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();permission(allowed);
        if(op==null || !actor.equals(op.get("actor")) || !source.action().name().equals(op.get("action")) || !prepared.decisionSha256().equals(op.get("sha256"))
                || !Long.valueOf(source.createdAt()).equals(op.get("createdAt")) || !Long.valueOf(source.expiresAt()).equals(op.get("expiresAt"))
                || !prepared.mutationId().equals(op.get("mutationId")) || !prepared.binding().requestId().equals(op.get("requestId")) || !(op.get("finishedAt") instanceof Date))return new Result("UNKNOWN",null);
        if("NOT_APPLIED".equals(op.get("state")) && op.get("afterSha256")==null)return new Result("NOT_APPLIED",null);
        String expected=digest(bytes(projected(source)));
        if("APPLIED".equals(op.get("state")) && expected.equals(op.get("afterSha256"))) {
            var record=ReviewRepairIo.collection(mongo.getCollection(ADMISSIONS).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY))
                    .find(new Document("_id",prepared.binding().requestId())).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();
            if(record==null || !Arrays.equals(bytes(record),bytes(admission(source,prepared,expected))))throw invalid();permission(allowed);return new Result("APPLIED",expected);
        }
        return new Result("UNKNOWN",null);
    }
    static Document projected(ReviewSnapshotArchive.Snapshot source) {
        var payload=decode(source.versionBytes());var held=decode(payload.get("heldVersion",Binary.class).getData());var scan=held.get("scanResult",Document.class);
        scan.put("scanState","REMOTE_REVIEW");scan.put("scanTimestamp",source.createdAt());scan.put("remoteReview",payload.get("binding",Document.class));return held;
    }
    private static Document admission(ReviewSnapshotArchive.Snapshot source,ProjectMutationAdmissionPreparation.Prepared prepared,String afterSha) {
        return new Document("_id",prepared.binding().requestId()).append("decisionId",prepared.id()).append("decisionSha256",prepared.decisionSha256())
                .append("projectId",source.projectId()).append("versionId",prepared.versionId()).append("mutationId",prepared.mutationId())
                .append("actor",source.actorId()).append("heldSha256",prepared.heldSha256()).append("afterSha256",afterSha).append("state","ACTIVE");
    }
    private static Document decode(byte[] bytes){return new RawBsonDocument(bytes).decode(new DocumentCodec());}
    private static byte[] bytes(Document value){var buffer=new RawBsonDocument(value,new DocumentCodec()).getByteBuffer().asNIO();var result=new byte[buffer.remaining()];buffer.get(result);return result;}
    private static String digest(byte[] value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception failure){throw new IllegalStateException(failure);}}
    private static void permission(BooleanSupplier allowed){if(!allowed.getAsBoolean())throw new SecurityException("Mutation activation is not permitted");}
    private static IllegalStateException invalid(){return new IllegalStateException("Mutation activation changed or is unavailable");}
}
