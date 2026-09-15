package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Atomically stages a retained replacement. HELD admissions cannot dispatch remote work. */
public final class ReviewReplacementExecutor {
    public static final String ADMISSIONS="review_replacement_admissions";
    public record Result(String state,String afterSha256) {}
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private static final TransactionOptions OPTIONS=TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT)
            .readPreference(ReadPreference.primary()).writeConcern(WriteConcern.MAJORITY.withJournal(true))
            .maxCommitTime(5000L,TimeUnit.MILLISECONDS).build();
    private final MongoTemplate mongo;
    private final ReviewReplacementPreparation preparation;
    private final RawReviewSnapshotReader reader;
    private final ReviewRepairJournal journal;
    private final MongoCollection<Document> projects,operations,admissions;

    public ReviewReplacementExecutor(MongoTemplate mongo,ReviewReplacementPreparation preparation,
            RawReviewSnapshotReader reader,ReviewRepairJournal journal) {
        this.mongo=Objects.requireNonNull(mongo);this.preparation=Objects.requireNonNull(preparation);
        this.reader=Objects.requireNonNull(reader);this.journal=Objects.requireNonNull(journal);
        projects=mongo.getCollection("projects");operations=mongo.getCollection(ReviewRepairJournal.COLLECTION)
                .withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY);
        admissions=mongo.getCollection(ADMISSIONS);
    }

    public Result stage(ReviewReplacementPreparation.Prepared prepared,String actor,BooleanSupplier permitted) {
        permission(permitted);var source=verify(prepared,actor,permitted).before();
        var original=new RawBsonDocument(source.versionBytes()).decode(new DocumentCodec());
        Object priorHold=original.get("replacementSecurityHold");
        if(priorHold!=null && (!(priorHold instanceof String value) || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
            throw new IllegalStateException("Replacement security hold is inconsistent");
        var binding=new Document();mongo.getConverter().write(prepared.replacement(),binding);
        var hello=ReviewRepairIo.database(mongo.getDb()).runCommand(new Document("hello",1),ReadPreference.primary());
        if(!(hello.get("setName") instanceof String) && !"isdbgrid".equals(hello.get("msg")))throw new IllegalStateException("Replacement requires transaction support");
        permission(permitted);
        var claim=journal.claim(new ReviewRepairPreparation.Prepared(source.id(),prepared.beforeSha256(),prepared.createdAt(),prepared.expiresAt()),
                actor,ReviewSnapshotArchive.Action.REPLACE_REVIEW,permitted);
        if(claim==null)return receipt(prepared,actor,permitted);
        boolean commitAttempted=false;
        try(var session=mongo.getMongoDatabaseFactory().getSession(ReviewRepairIo.sessionOptions())) {
            try {
                session.startTransaction(ReviewRepairIo.transactionOptions(OPTIONS));permission(permitted);
                var op=ReviewRepairIo.collection(operations,session).find(session,new Document("_id",claim.id()).append("token",claim.token()).append("state","EXECUTING"))
                        .collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();
                if(op==null)throw new IllegalStateException("Replacement claim changed");
                var current=reader.capture(session,source.projectId(),source.versionIndex(),original.getString("_id"));
                String state="NOT_APPLIED",after=null;
                if(Arrays.equals(source.versionBytes(),current.versionBytes())) {
                    var scan=new Document(original.get("scanResult",Document.class));
                    Object hold=priorHold!=null?priorHold:"BLOCK".equals(scan.get("verdict")) || "INFECTED".equals(scan.get("status"))?prepared.id():null;
                    scan.put("status","SCANNING");scan.put("scanState","REPLACEMENT_HELD");
                    scan.put("scanRequestId",prepared.replacement().requestId());scan.put("scanAttempt",prepared.replacement().attempt());
                    scan.put("manualRescan",true);scan.put("remoteReview",binding);scan.remove("remotePoll");scan.remove("remoteStatus");
                    scan.put("verdict",hold!=null?"BLOCK":"REVIEW");scan.put("artifactVerified",false);
                    for(String field:List.of("securityEvidence","reviewedContextSha256","reusedReviewVersion","reusedReviewOrigins"))scan.put(field,null);
                    scan.put("reusedReviewApprovedAt",0L);
                    var fields=new Document("scanResult",scan).append("reviewStatus","PENDING").append("scheduledPublishDate",null)
                            .append("replacementSecurityHold",hold)
                            .append("securityApprovedAt",0L)
                            .append("reviewReplacement",new Document("operationId",prepared.id()).append("beforeSha256",prepared.beforeSha256()).append("requestId",prepared.replacement().requestId()));
                    for(String field:List.of("securityApprovalProjectId","approvedSecurityEvidence","approvedSecurityContextSha256","approvedReviewOrigins","approvedFindingReviewHead"))fields.put(field,null);
                    String prefix="versions."+source.versionIndex()+".";var set=new Document();fields.forEach((key,value)->set.put(prefix+key,value));
                    var query=new Document("_id",source.projectId()).append("$expr",new Document("$and",List.of(
                            new Document("$gte",List.of("$$NOW",new Date(prepared.createdAt()))),
                            new Document("$lt",List.of("$$NOW",new Date(prepared.expiresAt()))))));
                    permission(permitted);
                    if(ReviewRepairIo.collection(projects,session).updateOne(session,query,new Document("$set",set)
                            .append("$unset",new Document(prefix+"reviewIsolation","")),new UpdateOptions().collation(BINARY)).getModifiedCount()==1) {
                        after=reader.capture(session,source.projectId(),source.versionIndex(),original.getString("_id")).sha256();
                        var admission=new Document("_id",prepared.id()).append("beforeArchiveId",source.id()).append("beforeSha256",prepared.beforeSha256())
                                .append("afterSha256",after).append("actor",actor).append("projectId",source.projectId()).append("versionIndex",source.versionIndex())
                                .append("requestId",prepared.replacement().requestId()).append("state","HELD");
                        ReviewRepairIo.collection(admissions,session).insertOne(session,admission);state="APPLIED";
                    }
                }
                var fields=new Document("state",state).append("afterSha256",after).append("replacementId",prepared.id());
                if(ReviewRepairIo.collection(operations,session).updateOne(session,op,List.of(new Document("$set",fields.append("finishedAt","$$NOW"))),new UpdateOptions().collation(BINARY)).getModifiedCount()!=1)
                    throw new IllegalStateException("Replacement claim changed");
                permission(permitted);commitAttempted=true;session.commitTransaction();return new Result(state,after);
            } catch(RuntimeException failure) {
                if(!commitAttempted)try{session.abortTransaction();}catch(RuntimeException ignored){}
                throw failure;
            }
        } catch(RuntimeException uncertain) {
            try(var cleanup=ReviewRepairIo.cleanup()) {
                if(uncertain instanceof SecurityException) {try{journal.markUnknown(claim,()->true);}catch(RuntimeException ignored){}throw uncertain;}
                try {var observed=receipt(prepared,actor,permitted);if(!observed.state().equals("UNKNOWN"))return observed;}catch(RuntimeException ignored){}
                try{journal.markUnknown(claim,()->true);}catch(RuntimeException ignored){}
                return new Result("UNKNOWN",null);
            }
        }
    }

    public Result receipt(ReviewReplacementPreparation.Prepared prepared,String actor,BooleanSupplier permitted) {
        permission(permitted);verify(prepared,actor,permitted);
        var op=ReviewRepairIo.collection(operations).find(new Document("_id",prepared.beforeArchiveId())).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();
        permission(permitted);
        if(op==null || !actor.equals(op.get("actor")) || !"REPLACE_REVIEW".equals(op.get("action"))
                || !prepared.id().equals(op.get("replacementId")) || !prepared.beforeSha256().equals(op.get("sha256"))
                || !Long.valueOf(prepared.createdAt()).equals(op.get("createdAt")) || !Long.valueOf(prepared.expiresAt()).equals(op.get("expiresAt"))
                || !(op.get("finishedAt") instanceof Date))return new Result("UNKNOWN",null);
        if("NOT_APPLIED".equals(op.get("state")) && op.get("afterSha256")==null)return new Result("NOT_APPLIED",null);
        if("APPLIED".equals(op.get("state")) && op.get("afterSha256") instanceof String sha && sha.matches("[0-9a-f]{64}"))return new Result("APPLIED",sha);
        return new Result("UNKNOWN",null);
    }
    private ReviewReplacementPreparation.Recovered verify(ReviewReplacementPreparation.Prepared prepared,String actor,BooleanSupplier permitted) {
        Objects.requireNonNull(prepared);
        var recovered=preparation.recoverEvidence(prepared.id(),actor,permitted);
        if(!prepared.equals(recovered.prepared()))throw new IllegalStateException("Replacement intent changed");
        return recovered;
    }
    private static void permission(BooleanSupplier permitted) {
        if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Replacement staging is not permitted");
    }
}
