package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.model.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Internal single-version transaction. Runtime access and grouped owner mutations must supply their own authority. */
public final class VersionMutationExecutor {
    public static final String REFERENCES="version_mutation_references";
    public record Result(String state,String afterSha256) {}
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private static final TransactionOptions OPTIONS=TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT).readPreference(ReadPreference.primary())
            .writeConcern(WriteConcern.MAJORITY.withJournal(true)).maxCommitTime(5000L,TimeUnit.MILLISECONDS).build();
    private static final Set<String> EDITABLE=Set.of("gameVersions","dependencies","incompatibleProjectIds","changelog","channel");
    private static final Set<String> RESET=Set.of("scanResult","reviewStatus","scheduledPublishDate","approvedSecurityEvidence","approvedSecurityContextSha256",
            "approvedReviewOrigins","approvedFindingReviewHead","securityApprovalProjectId","securityApprovedAt","approvedIssueBaselines");
    private final MongoTemplate mongo;private final VersionMutationPreparation preparation;private final ReviewSnapshotArchive archive;
    private final ReviewRepairJournal journal;private final RawReviewSnapshotReader reader;
    public VersionMutationExecutor(MongoTemplate mongo,VersionMutationPreparation preparation,ReviewSnapshotArchive archive,ReviewRepairJournal journal,RawReviewSnapshotReader reader) {
        this.mongo=Objects.requireNonNull(mongo);this.preparation=Objects.requireNonNull(preparation);this.archive=Objects.requireNonNull(archive);
        this.journal=Objects.requireNonNull(journal);this.reader=Objects.requireNonNull(reader);
    }
    public Result apply(VersionMutationPreparation.Prepared prepared,String actor,BooleanSupplier permitted) {
        permission(permitted);var recovered=preparation.recover(prepared.id(),actor,permitted);if(!prepared.equals(recovered.prepared()))throw invalid();
        var previous=receipt(prepared,actor,permitted);if(!previous.state().equals("UNKNOWN"))return previous;
        if(prepared.mutation()!=VersionMutationPreparation.Mutation.CONTEXT_EDIT && prepared.mutation()!=VersionMutationPreparation.Mutation.REMOVAL)
            throw new IllegalArgumentException("This mutation requires grouped persistence");
        var before=recovered.before();var original=decode(before.versionBytes());
        var replacement=recovered.after()==null?null:projected(prepared,original,decode(recovered.after().versionBytes()));
        if(replacement!=null && prepared.afterIndex()!=before.versionIndex())throw invalid();
        var source=archive.load(prepared.id());
        var hello=ReviewRepairIo.database(mongo.getDb()).runCommand(new Document("hello",1),ReadPreference.primary());
        if(!(hello.get("setName") instanceof String) && !"isdbgrid".equals(hello.get("msg")))throw invalid();
        var claim=journal.claim(new ReviewRepairPreparation.Prepared(source.id(),digest(source.versionBytes()),source.createdAt(),source.expiresAt()),
                actor,ReviewSnapshotArchive.Action.VERSION_MUTATION_INTENT,permitted);
        if(claim==null)return receipt(prepared,actor,permitted);
        boolean commitAttempted=false;
        try(var session=mongo.getMongoDatabaseFactory().getSession(ReviewRepairIo.sessionOptions())) {
            try {
                session.startTransaction(ReviewRepairIo.transactionOptions(OPTIONS));permission(permitted);
                var operations=ReviewRepairIo.collection(mongo.getCollection(ReviewRepairJournal.COLLECTION),session);
                var operation=operations.find(session,new Document("_id",claim.id()).append("token",claim.token()).append("state","EXECUTING"))
                        .collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();if(operation==null)throw invalid();
                var captured=reader.capture(session,before.projectId(),before.versionIndex(),original.getString("_id"));
                var projects=ReviewRepairIo.collection(mongo.getCollection("projects"),session);
                var project=projects.find(session,new Document("_id",before.projectId())).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();
                String state="NOT_APPLIED",after=null;
                if(Arrays.equals(captured.versionBytes(),before.versionBytes()) && project!=null
                        && project.get("status") instanceof String status && Set.of("DRAFT","PENDING","PUBLISHED","UNLISTED","PRIVATE").contains(status)) {
                    var versions=new ArrayList<>(project.getList("versions",Document.class));
                    // A visible published project must keep at least one version; the outer grouped mutation handles replacement uploads.
                    if(replacement!=null || versions.size()>1 || Set.of("DRAFT","PRIVATE").contains(status)) {
                        if(replacement==null)versions.remove(before.versionIndex());else versions.set(before.versionIndex(),replacement);
                        var expression=new Document("$and",List.of(new Document("$eq",List.of("$$ROOT",new Document("$literal",project))),
                                new Document("$gte",List.of("$$NOW",new Date(source.createdAt()))),new Document("$lt",List.of("$$NOW",new Date(source.expiresAt())))));
                        permission(permitted);
                        var changed=projects.updateOne(session,new Document("_id",before.projectId()).append("$expr",expression),
                                new Document("$set",new Document("versions",versions).append("updatedAt",java.time.LocalDateTime.now().toString())),new UpdateOptions().collation(BINARY));
                        if(changed.getModifiedCount()==1) {
                            after=replacement==null?null:reader.capture(session,before.projectId(),before.versionIndex(),original.getString("_id")).sha256();
                            var reference=new Document("_id",prepared.id()).append("actor",actor).append("projectId",before.projectId()).append("versionId",original.get("_id"))
                                    .append("beforeArchiveId",prepared.beforeArchiveId()).append("beforeSha256",prepared.beforeSha256()).append("afterSha256",after)
                                    .append("state",replacement==null?"REMOVED":"HELD");
                            ReviewRepairIo.collection(mongo.getCollection(REFERENCES),session).insertOne(session,reference);state="APPLIED";
                        }
                    }
                }
                if(operations.updateOne(session,operation,List.of(new Document("$set",new Document("state",state).append("afterSha256",after).append("finishedAt","$$NOW"))),
                        new UpdateOptions().collation(BINARY)).getModifiedCount()!=1)throw invalid();
                permission(permitted);commitAttempted=true;session.commitTransaction();return new Result(state,after);
            } catch(RuntimeException failure) {if(!commitAttempted)try{session.abortTransaction();}catch(RuntimeException ignored){}throw failure;}
        } catch(RuntimeException uncertain) {
            try(var cleanup=ReviewRepairIo.cleanup()) {
                if(uncertain instanceof SecurityException){try{journal.markUnknown(claim,()->true);}catch(RuntimeException ignored){}throw uncertain;}
                try{var observed=receipt(prepared,actor,permitted);if(!observed.state().equals("UNKNOWN"))return observed;}catch(RuntimeException ignored){}
                try{journal.markUnknown(claim,()->true);}catch(RuntimeException ignored){}return new Result("UNKNOWN",null);
            }
        }
    }
    public Result receipt(VersionMutationPreparation.Prepared prepared,String actor,BooleanSupplier permitted) {
        permission(permitted);if(!prepared.equals(preparation.recover(prepared.id(),actor,permitted).prepared()))throw invalid();
        var source=archive.load(prepared.id());
        var op=ReviewRepairIo.collection(mongo.getCollection(ReviewRepairJournal.COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY))
                .find(new Document("_id",prepared.id())).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();permission(permitted);
        if(op==null || !actor.equals(op.get("actor")) || !source.action().name().equals(op.get("action")) || !digest(source.versionBytes()).equals(op.get("sha256"))
                || !Long.valueOf(source.createdAt()).equals(op.get("createdAt")) || !Long.valueOf(source.expiresAt()).equals(op.get("expiresAt")) || !(op.get("finishedAt") instanceof Date))return new Result("UNKNOWN",null);
        if("NOT_APPLIED".equals(op.get("state")) && op.get("afterSha256")==null)return new Result("NOT_APPLIED",null);
        if("APPLIED".equals(op.get("state")) && (prepared.mutation()==VersionMutationPreparation.Mutation.REMOVAL?op.get("afterSha256")==null:
                op.get("afterSha256") instanceof String sha && sha.matches("[0-9a-f]{64}")))return new Result("APPLIED",op.getString("afterSha256"));
        return new Result("UNKNOWN",null);
    }
    private static Document projected(VersionMutationPreparation.Prepared prepared,Document before,Document proposed) {
        return projectVersion(prepared.id(),prepared.beforeSha256(),prepared.id(),before,proposed);
    }
    static Document projectVersion(String operationId,String beforeSha256,String requestSeed,Document before,Document proposed) {
        var fields=new HashSet<>(before.keySet());fields.addAll(proposed.keySet());
        for(var field:fields)if(!EDITABLE.contains(field) && !RESET.contains(field)
                && (before.containsKey(field)!=proposed.containsKey(field) || !Arrays.equals(bytes(new Document("v",before.get(field))),bytes(new Document("v",proposed.get(field))))))throw invalid();
        var next=new Document(before);for(var field:EDITABLE){if(proposed.containsKey(field))next.put(field,proposed.get(field));else next.remove(field);}
        var old=before.get("scanResult",Document.class);Object hold=before.get("replacementSecurityHold");
        if(hold!=null && (!(hold instanceof String id) || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))throw invalid();
        if(hold==null && old!=null && ("BLOCK".equals(old.get("verdict")) || "INFECTED".equals(old.get("status"))))hold=operationId;
        String request=UUID.nameUUIDFromBytes(("version-mutation-request-1:"+requestSeed).getBytes(StandardCharsets.UTF_8)).toString();
        if(before.get("retainedRemoteReview")!=null && !(before.get("retainedRemoteReview") instanceof Document))throw invalid();
        if(old!=null && old.get("scanAttempt")==null)throw invalid();
        Object priorAttempt=old==null?before.get("retainedRemoteReview") instanceof Document retained?retained.get("attempt"):null:old.get("scanAttempt");
        if(priorAttempt!=null && (!(priorAttempt instanceof Integer || priorAttempt instanceof Long) || ((Number)priorAttempt).longValue()<1 || ((Number)priorAttempt).longValue()>=Integer.MAX_VALUE))throw invalid();
        int attempt=priorAttempt==null?1:Math.toIntExact(((Number)priorAttempt).longValue()+1);
        var scan=new Document("status","SCANNING").append("scanState","MUTATION_HELD").append("scanRequestId",request)
                .append("scanAttempt",attempt).append("scanTimestamp",System.currentTimeMillis()).append("manualRescan",false).append("verdict",hold==null?"REVIEW":"BLOCK");
        next.put("scanResult",scan);for(var field:RESET)if(!field.equals("scanResult"))next.put(field,null);
        next.put("reviewStatus","PENDING");next.put("securityApprovedAt",0L);next.put("replacementSecurityHold",hold);
        next.put("versionMutation",new Document("operationId",operationId).append("beforeSha256",beforeSha256).append("requestId",request));
        return next;
    }
    private static Document decode(byte[] value){return new RawBsonDocument(value).decode(new DocumentCodec());}
    private static byte[] bytes(Document value){var buffer=new RawBsonDocument(value,new DocumentCodec()).getByteBuffer().asNIO();var bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;}
    private static String digest(byte[] value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private static void permission(BooleanSupplier permitted){if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Version mutation is not permitted");}
    private static IllegalStateException invalid(){return new IllegalStateException("Version mutation cannot be applied to the current state");}
}
