package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.model.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Atomic internal group consumption. Never dispatches held scans or authorizes artifact deletion. */
public final class ProjectMutationExecutor {
    public static final String REFERENCES="project_mutation_references";
    public record Result(String state,String afterSha256) {}
    private static final Set<String> NEW_FIELDS=Set.of("_id","_class","versionNumber","gameVersions","fileUrl","overrideFileUrl","hash","manifestId","manifestVersion",
            "curseForgeFingerprint","downloadCount","releaseDate","changelog","dependencies","incompatibleProjectIds","channel","modpackConfigs",
            "scanResult","reviewStatus","rejectionReason","scheduledPublishDate","securityApprovedAt","retainedRemoteReview","versionMutation","reviewReplacement",
            "reviewIsolation","replacementSecurityHold","findingReviewHead","approvedFindingReviewHead","approvedSecurityEvidence","approvedReviewOrigins",
            "approvedIssueBaselines","securityApprovalProjectId","approvedSecurityContextSha256");
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private static final TransactionOptions OPTIONS=TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT).readPreference(ReadPreference.primary())
            .writeConcern(WriteConcern.MAJORITY.withJournal(true)).maxCommitTime(5000L,TimeUnit.MILLISECONDS).build();
    private final MongoTemplate mongo;private final ProjectMutationPreparation preparation;private final ReviewSnapshotArchive archive;private final ReviewRepairJournal journal;
    public ProjectMutationExecutor(MongoTemplate mongo,ProjectMutationPreparation preparation,ReviewSnapshotArchive archive,ReviewRepairJournal journal) {
        this.mongo=Objects.requireNonNull(mongo);this.preparation=Objects.requireNonNull(preparation);this.archive=Objects.requireNonNull(archive);this.journal=Objects.requireNonNull(journal);
    }
    public Result apply(ProjectMutationPreparation.Prepared prepared,String actor,BooleanSupplier permitted) {
        permission(permitted);var recovered=preparation.recover(prepared.id(),actor,permitted);if(!prepared.equals(recovered.prepared()))throw invalid();
        var previous=receipt(prepared,actor,permitted);if(!"UNKNOWN".equals(previous.state()))return previous;
        var before=decode(recovered.before().versionBytes());var proposed=decode(recovered.after().versionBytes());
        var after=project(recovered,before,proposed);String afterSha=digest(bytes(after));
        var intent=archive.load(prepared.id());
        var applied=new ReviewSnapshotArchive.Snapshot(appliedArchiveId(prepared.id()),intent.projectId(),0,actor,
                ReviewSnapshotArchive.Action.PROJECT_MUTATION_APPLIED,prepared.createdAt(),prepared.expiresAt(),bytes(after));
        permission(permitted);
        try{archive.retain(applied);}catch(RuntimeException uncertain){var found=archive.find(applied.id());if(found==null || !same(applied,found))throw uncertain;}
        permission(permitted);
        var hello=ReviewRepairIo.database(mongo.getDb()).runCommand(new Document("hello",1),ReadPreference.primary());
        if(!(hello.get("setName") instanceof String) && !"isdbgrid".equals(hello.get("msg")))throw invalid();
        var claim=journal.claim(new ReviewRepairPreparation.Prepared(intent.id(),digest(intent.versionBytes()),intent.createdAt(),intent.expiresAt()),actor,ReviewSnapshotArchive.Action.PROJECT_MUTATION_INTENT,permitted);
        if(claim==null)return receipt(prepared,actor,permitted);boolean commitAttempted=false;
        try(var session=mongo.getMongoDatabaseFactory().getSession(ReviewRepairIo.sessionOptions())) {
            try {
                session.startTransaction(ReviewRepairIo.transactionOptions(OPTIONS));permission(permitted);
                var operations=ReviewRepairIo.collection(mongo.getCollection(ReviewRepairJournal.COLLECTION),session);
                var op=operations.find(session,new Document("_id",claim.id()).append("token",claim.token()).append("state","EXECUTING")).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();if(op==null)throw invalid();
                var projects=ReviewRepairIo.collection(mongo.getCollection("projects"),session);
                var query=new Document("_id",intent.projectId()).append("$expr",new Document("$and",List.of(
                        new Document("$eq",List.of("$$ROOT",new Document("$literal",before))),new Document("$gte",List.of("$$NOW",new Date(intent.createdAt()))),
                        new Document("$lt",List.of("$$NOW",new Date(intent.expiresAt()))))));
                var rawCurrent=ReviewRepairIo.collection(mongo.getCollection("projects").withDocumentClass(RawBsonDocument.class),session)
                        .find(session,new Document("_id",intent.projectId())).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();
                boolean exact=false;
                if(rawCurrent!=null){var buffer=rawCurrent.getByteBuffer().asNIO();var currentBytes=new byte[buffer.remaining()];buffer.get(currentBytes);exact=Arrays.equals(currentBytes,recovered.before().versionBytes());}
                permission(permitted);String state="NOT_APPLIED",appliedSha=null;
                if(exact && projects.replaceOne(session,query,after,new ReplaceOptions().collation(BINARY)).getModifiedCount()==1) {
                    var changes=new ArrayList<Document>();for(var transition:recovered.transitions()) {
                        var version=transition.afterIndex()<0?null:after.getList("versions",Document.class).get(transition.afterIndex());
                        String versionSha=version==null?null:digest(bytes(version));
                        if(Objects.equals(versionSha,transition.beforeSha256()) && transition.beforeIndex()==transition.afterIndex())continue;
                        var scan=version==null?null:version.get("scanResult",Document.class);
                        String versionState=version==null?"REMOVED":scan!=null && "MUTATION_HELD".equals(scan.get("scanState"))?"HELD":"RETAINED";
                        changes.add(new Document("versionId",transition.versionId()).append("beforeIndex",transition.beforeIndex()).append("afterIndex",transition.afterIndex())
                                .append("beforeSha256",transition.beforeSha256()).append("afterSha256",versionSha).append("state",versionState));
                    }
                    ReviewRepairIo.collection(mongo.getCollection(REFERENCES),session).insertOne(session,new Document("_id",prepared.id()).append("projectId",intent.projectId())
                            .append("actor",actor).append("beforeArchiveId",prepared.beforeArchiveId()).append("beforeSha256",prepared.beforeSha256())
                            .append("afterArchiveId",applied.id()).append("afterSha256",afterSha).append("versions",changes));
                    state="APPLIED";appliedSha=afterSha;
                }
                if(operations.updateOne(session,op,List.of(new Document("$set",new Document("state",state).append("afterSha256",appliedSha).append("afterArchiveId",appliedSha==null?null:applied.id()).append("finishedAt","$$NOW"))),new UpdateOptions().collation(BINARY)).getModifiedCount()!=1)throw invalid();
                permission(permitted);commitAttempted=true;session.commitTransaction();return new Result(state,appliedSha);
            } catch(RuntimeException failure){if(!commitAttempted)try{session.abortTransaction();}catch(RuntimeException ignored){}throw failure;}
        } catch(RuntimeException uncertain) {
            try(var cleanup=ReviewRepairIo.cleanup()) {
                if(uncertain instanceof SecurityException){try{journal.markUnknown(claim,()->true);}catch(RuntimeException ignored){}throw uncertain;}
                try{var observed=receipt(prepared,actor,permitted);if(!"UNKNOWN".equals(observed.state()))return observed;}catch(RuntimeException ignored){}
                try{journal.markUnknown(claim,()->true);}catch(RuntimeException ignored){}return new Result("UNKNOWN",null);
            }
        }
    }
    public Result receipt(ProjectMutationPreparation.Prepared prepared,String actor,BooleanSupplier permitted) {
        permission(permitted);if(!prepared.equals(preparation.recover(prepared.id(),actor,permitted).prepared()))throw invalid();var source=archive.load(prepared.id());
        var op=ReviewRepairIo.collection(mongo.getCollection(ReviewRepairJournal.COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY))
                .find(new Document("_id",prepared.id())).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();permission(permitted);
        if(op==null || !actor.equals(op.get("actor")) || !source.action().name().equals(op.get("action")) || !digest(source.versionBytes()).equals(op.get("sha256"))
                || !Long.valueOf(source.createdAt()).equals(op.get("createdAt")) || !Long.valueOf(source.expiresAt()).equals(op.get("expiresAt")) || !(op.get("finishedAt") instanceof Date))return new Result("UNKNOWN",null);
        if("NOT_APPLIED".equals(op.get("state")) && op.get("afterSha256")==null)return new Result("NOT_APPLIED",null);
        if("APPLIED".equals(op.get("state")) && op.get("afterSha256") instanceof String sha && sha.matches("[0-9a-f]{64}")
                && appliedArchiveId(prepared.id()).equals(op.get("afterArchiveId"))) {
            var applied=archive.load(op.getString("afterArchiveId"));
            if(!applied.projectId().equals(source.projectId()) || applied.versionIndex()!=0 || !actor.equals(applied.actorId())
                    || applied.action()!=ReviewSnapshotArchive.Action.PROJECT_MUTATION_APPLIED || applied.createdAt()!=prepared.createdAt()
                    || applied.expiresAt()!=prepared.expiresAt() || !sha.equals(digest(applied.versionBytes())))throw invalid();
            permission(permitted);return new Result("APPLIED",sha);
        }
        return new Result("UNKNOWN",null);
    }
    private static Document project(ProjectMutationPreparation.Recovered recovered,Document before,Document proposed) {
        var prepared=recovered.prepared();Object status=before.get("status");
        if(!(status instanceof String) || !Set.of("DRAFT","PENDING","PUBLISHED","UNLISTED","PRIVATE").contains(status)
                )throw invalid();
        boolean classificationChanged=!Objects.equals(before.get("classification"),proposed.get("classification"));
        if(classificationChanged && (!(before.get("classification") instanceof String oldType) || !(proposed.get("classification") instanceof String newType)
                || !Set.of("DATA","PLUGIN").contains(oldType) || !Set.of("DATA","PLUGIN").contains(newType)
                || recovered.transitions().stream().noneMatch(transition->transition.beforeIndex()<0)))throw invalid();
        var old=before.getList("versions",Document.class);var desired=proposed.getList("versions",Document.class);var versions=new ArrayList<Document>(Collections.nCopies(desired.size(),null));
        for(var transition:recovered.transitions()) {
            if(transition.afterIndex()<0)continue;var next=desired.get(transition.afterIndex());Document original;
            if(transition.beforeIndex()<0) {
                if(!NEW_FIELDS.containsAll(next.keySet()) || next.get("_class")!=null && !"net.modtale.model.project.ProjectVersion".equals(next.get("_class")))throw invalid();
                for(var field:List.of("retainedRemoteReview","versionMutation","reviewReplacement","reviewIsolation","replacementSecurityHold","findingReviewHead","approvedFindingReviewHead","approvedSecurityEvidence","approvedReviewOrigins","approvedIssueBaselines","securityApprovalProjectId","approvedSecurityContextSha256"))if(next.get(field)!=null)throw invalid();
                if(next.get("reviewStatus")!=null && !"PENDING".equals(next.get("reviewStatus")))throw invalid();
                original=new Document(next);original.put("scanResult",null);original.put("securityApprovedAt",0L);
            } else original=old.get(transition.beforeIndex());
            boolean held=transition.beforeIndex()<0 || transition.changes().stream().anyMatch(change->change!=VersionReviewTransition.Change.METADATA)
                    || prepared.mutation()==ProjectMutationPreparation.Mutation.SUBMISSION && (original.get("scanResult")==null
                    || original.get("versionMutation")==null && original.get("scanResult") instanceof Document queued
                    && "SCANNING".equals(queued.get("status")) && queued.get("remoteReview")==null);
            if(held && "MODPACK".equals(before.get("classification")) && next.get("fileUrl")==null
                    && original.get("fileUrl") instanceof String cached && cached.endsWith(".zip")
                    && transition.changes().contains(VersionReviewTransition.Change.CONTEXT)) {
                original=new Document(original);if(next.containsKey("fileUrl"))original.put("fileUrl",null);else original.remove("fileUrl");
            }
            if(held)next=VersionMutationExecutor.projectVersion(prepared.id(),transition.beforeSha256()==null?prepared.beforeSha256():transition.beforeSha256(),
                    prepared.id()+":"+transition.versionId(),original,next,prepared.createdAt());
            else {
                // Only the listed owner metadata changes are copied; all review and unknown fields remain byte-preserved.
                var retained=new Document(original);for(var field:List.of("gameVersions","dependencies","incompatibleProjectIds","changelog","channel","versionNumber","releaseDate")) {
                    if(next.containsKey(field))retained.put(field,next.get(field));else retained.remove(field);
                }next=retained;
            }
            versions.set(transition.afterIndex(),next);
        }
        if(versions.isEmpty() && !Set.of("DRAFT","PRIVATE").contains(status))throw invalid();
        var after=new Document(before);if(classificationChanged)after.put("classification",proposed.get("classification"));after.put("versions",versions);after.put("updatedAt",java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(prepared.createdAt()),java.time.ZoneOffset.UTC).toString());
        if(prepared.mutation()==ProjectMutationPreparation.Mutation.SUBMISSION){after.put("status","PENDING");after.put("expiresAt",null);}
        else if(proposed.containsKey("childProjectIds"))after.put("childProjectIds",proposed.get("childProjectIds"));
        if(bytes(after).length>ReviewSnapshotArchive.MAX_BYTES)throw invalid();return after;
    }
    private static String appliedArchiveId(String id){return UUID.nameUUIDFromBytes(("project-mutation-applied-1:"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
    private static boolean same(ReviewSnapshotArchive.Snapshot a,ReviewSnapshotArchive.Snapshot b){return a.id().equals(b.id()) && a.projectId().equals(b.projectId()) && a.versionIndex()==b.versionIndex() && a.actorId().equals(b.actorId()) && a.action()==b.action() && a.createdAt()==b.createdAt() && a.expiresAt()==b.expiresAt() && Arrays.equals(a.versionBytes(),b.versionBytes());}
    private static Document decode(byte[] data){return new RawBsonDocument(data).decode(new DocumentCodec());}
    private static byte[] bytes(Document doc){var buffer=new RawBsonDocument(doc,new DocumentCodec()).getByteBuffer().asNIO();var data=new byte[buffer.remaining()];buffer.get(data);return data;}
    private static String digest(byte[] data){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    private static void permission(BooleanSupplier permitted){if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Grouped mutation is not permitted");}
    private static IllegalStateException invalid(){return new IllegalStateException("Grouped mutation cannot be applied to the current state");}
}
