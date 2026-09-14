package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import net.modtale.model.project.*;
import net.modtale.service.security.scan.ArtifactReviewContext;
import org.bson.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Isolates broken local review state; never approves or starts/cancels a remote review. */
public final class ReviewIsolationExecutor {
    public record Result(String state,String afterSha256) {}
    private final MongoTemplate mongo;
    private final ReviewSnapshotArchive archive;
    private final RawReviewSnapshotReader reader;
    private final ReviewRepairJournal journal;
    private final ReviewRepairOperationReader operationReader;
    private final MongoCollection<Document> projects,operations;
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private static final TransactionOptions OPTIONS=TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT)
            .readPreference(ReadPreference.primary()).writeConcern(WriteConcern.MAJORITY.withJournal(true)).maxCommitTime(5000L,TimeUnit.MILLISECONDS).build();
    public ReviewIsolationExecutor(MongoTemplate mongo,ReviewSnapshotArchive archive,RawReviewSnapshotReader reader,ReviewRepairJournal journal) {
        this.mongo=mongo;this.archive=archive;this.reader=reader;this.journal=journal;operationReader=new ReviewRepairOperationReader(mongo);
        projects=mongo.getCollection("projects");operations=mongo.getCollection(ReviewRepairJournal.COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY);
    }
    public void initializeDiscovery(){operationReader.initialize();}
    public ReviewRepairOperationReader.Page operations(String actor,String cursor,int limit){return operationReader.page(actor,cursor,limit);}
    public record Recovered(ReviewRepairPreparation.Prepared prepared,Receipt receipt) {}
    public Recovered recover(String id,String actor) {
        var source=archive.load(id);
        if(source.action()!=ReviewSnapshotArchive.Action.ISOLATE_REVIEW || !source.actorId().equals(actor))throw new SecurityException("Repair recovery is not permitted");
        var prepared=new ReviewRepairPreparation.Prepared(source.id(),sourceDigest(source),source.createdAt(),source.expiresAt());
        return new Recovered(prepared,receipt(source,prepared));
    }
    public boolean eligible(RawReviewSnapshotReader.Captured captured) {
        var source=captured.forArchive("00000000-0000-0000-0000-000000000000","inspection",ReviewSnapshotArchive.Action.ISOLATE_REVIEW,1,2);
        var original=new RawBsonDocument(source.versionBytes()).decode(new org.bson.codecs.DocumentCodec());
        return fields(original,source)!=null;
    }
    public record Receipt(Result outcome,Object projectId,int versionIndex,String versionId,String beforeSha256) {}
    public Receipt receipt(ReviewRepairPreparation.Prepared prepared,String actor) {
        return receipt(verifiedSource(prepared,actor),prepared);
    }
    private Receipt receipt(ReviewSnapshotArchive.Snapshot source,ReviewRepairPreparation.Prepared prepared) {
        String versionId=new RawBsonDocument(source.versionBytes()).getString("_id").getValue();
        return new Receipt(outcome(prepared,source.actorId()),source.projectId(),source.versionIndex(),versionId,prepared.sha256());
    }
    private ReviewSnapshotArchive.Snapshot verifiedSource(ReviewRepairPreparation.Prepared prepared,String actor) {
        var source=archive.load(prepared.id());
        if(source.action()!=ReviewSnapshotArchive.Action.ISOLATE_REVIEW || !source.actorId().equals(actor))throw new SecurityException("Repair execution is not permitted");
        if(!sourceDigest(source).equals(prepared.sha256()) || source.createdAt()!=prepared.createdAt() || source.expiresAt()!=prepared.expiresAt())throw new IllegalStateException("Repair intent changed");
        return source;
    }
    private static String sourceDigest(ReviewSnapshotArchive.Snapshot source) {
        try {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source.versionBytes()));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public Result execute(ReviewRepairPreparation.Prepared prepared,String actor,BooleanSupplier permitted) {
        permission(permitted);var source=verifiedSource(prepared,actor);
        var original=new RawBsonDocument(source.versionBytes()).decode(new org.bson.codecs.DocumentCodec());
        Document fields=fields(original,source);if(fields==null)return new Result("INELIGIBLE",null);
        var hello=ReviewRepairIo.database(mongo.getDb()).runCommand(new Document("hello",1),ReadPreference.primary());
        if(!(hello.get("setName") instanceof String) && !"isdbgrid".equals(hello.get("msg")))throw new IllegalStateException("Review isolation requires transaction support");
        permission(permitted);
        var claim=journal.claim(prepared,actor,ReviewSnapshotArchive.Action.ISOLATE_REVIEW,permitted);
        if(claim==null)return outcome(prepared,actor);
        boolean commitAttempted=false;
        try(var session=mongo.getMongoDatabaseFactory().getSession(ReviewRepairIo.sessionOptions())) {
            try {
                session.startTransaction(ReviewRepairIo.transactionOptions(OPTIONS));permission(permitted);
                var op=ReviewRepairIo.collection(operations,session).find(session,new Document("_id",claim.id()).append("token",claim.token()).append("state","EXECUTING")).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();
                if(op==null)throw new IllegalStateException("Repair claim changed");
                var current=reader.capture(session,source.projectId(),source.versionIndex(),original.getString("_id"));
                String result;String after=null;
                if(!Arrays.equals(source.versionBytes(),current.versionBytes()))result="NOT_APPLIED";
                else {
                    String prefix="versions."+source.versionIndex()+".";var set=new Document();fields.forEach((key,value)->set.put(prefix+key,value));
                    var conditions=new ArrayList<Document>();conditions.add(new Document("$gte",List.of("$$NOW",new Date(source.createdAt()))));conditions.add(new Document("$lt",List.of("$$NOW",new Date(source.expiresAt()))));
                    var scan=original.get("scanResult",Document.class);var poll=scan.get("remotePoll");
                    if(poll instanceof Document p && p.get("leaseUntil") instanceof Date lease)conditions.add(new Document("$gte",List.of("$$NOW",lease)));
                    permission(permitted);
                    long changed=ReviewRepairIo.collection(projects,session).updateOne(session,new Document("_id",source.projectId()).append("$expr",new Document("$and",conditions)),
                            new Document("$set",set).append("$currentDate",new Document(prefix+"reviewIsolation.isolatedAt",true)),new UpdateOptions().collation(BINARY)).getModifiedCount();
                    if(changed==1) {after=reader.capture(session,source.projectId(),source.versionIndex(),original.getString("_id")).sha256();result="APPLIED";}
                    else result="NOT_APPLIED";
                }
                var outcome=new Document("state",result).append("afterSha256",after);
                if(ReviewRepairIo.collection(operations,session).updateOne(session,op,List.of(new Document("$set",outcome.append("finishedAt","$$NOW"))),new UpdateOptions().collation(BINARY)).getModifiedCount()!=1)throw new IllegalStateException("Repair claim changed");
                permission(permitted);commitAttempted=true;session.commitTransaction();return new Result(result,after);
            } catch(RuntimeException failure) {
                if(!commitAttempted)try{session.abortTransaction();}catch(RuntimeException ignored){}
                throw failure;
            }
        } catch(RuntimeException uncertain) {
            try(var cleanup=ReviewRepairIo.cleanup()) {
            // Conservative bookkeeping for an already-owned operation remains necessary after permission loss.
            if(uncertain instanceof SecurityException) {try{journal.markUnknown(claim,()->true);}catch(RuntimeException ignored){}throw uncertain;}
            try {var observed=outcome(prepared,actor);if(!observed.state().equals("UNKNOWN"))return observed;}catch(RuntimeException ignored){}
            try{journal.markUnknown(claim,()->true);}catch(RuntimeException ignored){}
            return new Result("UNKNOWN",null);
            }
        }
    }
    private Result outcome(ReviewRepairPreparation.Prepared prepared,String actor) {
        var record=ReviewRepairIo.collection(operations).find(new Document("_id",prepared.id())).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();
        if(record==null || !actor.equals(record.get("actor")) || !prepared.sha256().equals(record.get("sha256"))
                || !Long.valueOf(prepared.createdAt()).equals(record.get("createdAt")) || !Long.valueOf(prepared.expiresAt()).equals(record.get("expiresAt"))
                || !"ISOLATE_REVIEW".equals(record.get("action")))return new Result("UNKNOWN",null);
        if(!(record.get("finishedAt") instanceof Date))return new Result("UNKNOWN",null);
        if("NOT_APPLIED".equals(record.get("state")) && record.get("afterSha256")==null)return new Result("NOT_APPLIED",null);
        if("APPLIED".equals(record.get("state")) && record.get("afterSha256") instanceof String sha && sha.matches("[0-9a-f]{64}"))return new Result("APPLIED",sha);
        return new Result("UNKNOWN",null);
    }
    private Document fields(Document original,ReviewSnapshotArchive.Snapshot source) {
        try {
            var encoded=new RawBsonDocument(original,new org.bson.codecs.DocumentCodec()).getByteBuffer().asNIO();byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);
            if(!Arrays.equals(bytes,source.versionBytes()) || !(original.get("_id") instanceof String) || !"PENDING".equals(original.get("reviewStatus"))
                    || !(original.get("scanResult") instanceof Document scan) || !"SCANNING".equals(scan.get("status")))return null;
            if(original.containsKey("reviewIsolation") && !(original.get("reviewIsolation") instanceof Document))return null;
            var changed=new Document(scan);changed.put("remotePoll",null);changed.put("remoteStatus",null);
            boolean broken=scan.containsKey("remotePoll") && invalidPoll(scan.get("remotePoll"));
            String state=scan.getString("scanState");broken|=!Set.of("QUEUED","SCANNING","REMOTE_REVIEW").contains(Objects.toString(state,""));
            RemoteReviewBinding binding=null;
            if(scan.get("remoteReview")!=null) {
                try{binding=mongo.getConverter().read(RemoteReviewBinding.class,(Document)scan.get("remoteReview"));}
                catch(RuntimeException invalid){broken=true;changed.put("remoteReview",null);}
            } else if("REMOTE_REVIEW".equals(state))broken=true;
            changed.put("status","FAILED");changed.put("scanState","REMOTE_ISOLATED");changed.put("verdict","BLOCK".equals(scan.get("verdict"))?"BLOCK":"REVIEW");
            for(String field:List.of("securityEvidence","reviewedContextSha256","reusedReviewVersion","reusedReviewOrigins"))changed.put(field,null);
            changed.put("artifactVerified",false);changed.put("holdUntilTimestamp",0L);changed.put("reusedReviewApprovedAt",0L);
            var patch=new Document("scanResult",changed).append("scheduledPublishDate",null).append("securityApprovedAt",0L);
            for(String field:List.of("securityApprovalProjectId","approvedSecurityEvidence","approvedSecurityContextSha256","approvedReviewOrigins","approvedFindingReviewHead"))patch.put(field,null);
            var candidate=new Document(original);candidate.putAll(patch);var typed=mongo.getConverter().read(ProjectVersion.class,candidate);
            if(binding!=null)broken|=!source.projectId().toString().equals(binding.projectId()) || !typed.getId().equals(binding.versionId())
                    || !Objects.equals(typed.getHash(),binding.artifactSha256()) || !Objects.equals(typed.getFileUrl(),binding.filePath())
                    || !Objects.equals(ArtifactReviewContext.automaticallyReviewableFingerprint(typed),binding.contextSha256())
                    || !Objects.equals(typed.getScanResult().getScanRequestId(),binding.requestId()) || typed.getScanResult().getScanAttempt()!=binding.attempt()
                    || typed.getScanResult().isManualRescan()!=binding.manualRescan() || !"REMOTE_REVIEW".equals(state);
            if(!broken)return null;
            patch.put("reviewIsolation.operationId",source.id());patch.put("reviewIsolation.actorId",source.actorId());
            patch.put("reviewIsolation.beforeSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source.versionBytes())));
            return patch;
        } catch(Exception invalid){return null;}
    }
    private static boolean invalidPoll(Object value) {
        return !(value instanceof Document p) || p.size()!=3 || !(p.get("leaseUntil") instanceof Date) || !(p.get("nextPollAt") instanceof Date)
                || !p.containsKey("token") || p.get("token")!=null && (!(p.get("token") instanceof String token) || !token.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }
    private static void permission(BooleanSupplier permitted){if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Review isolation is not permitted");}
}
