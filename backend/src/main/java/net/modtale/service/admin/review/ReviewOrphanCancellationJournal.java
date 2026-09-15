package net.modtale.service.admin.review;

import com.mongodb.*;
import net.modtale.service.security.scan.RemoteReviewClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** One immutable cancellation intent per isolation, with no remote dispatch or automatic claim recovery. */
public final class ReviewOrphanCancellationJournal {
    public static final String COLLECTION="review_orphan_cancellations";
    public record Prepared(String isolationId,String targetSha256,long createdAt,long expiresAt) {}
    public record Claim(Prepared prepared,String token,ReviewOrphanTargetResolver.Target target) {}
    public record Observation(String kind,RemoteReviewClient.Status status,Integer httpStatus) {
        public Observation {
            if(!Set.of("REMOTE_STATUS","NOT_FOUND","CONTEXT_CONFLICT","UNAVAILABLE","UNKNOWN").contains(Objects.toString(kind,""))
                    || "REMOTE_STATUS".equals(kind)!=(status!=null) || status!=null && httpStatus!=null
                    || "NOT_FOUND".equals(kind) && !Integer.valueOf(404).equals(httpStatus)
                    || "CONTEXT_CONFLICT".equals(kind) && !Integer.valueOf(409).equals(httpStatus)
                    || "UNAVAILABLE".equals(kind) && !Set.of(401,403,429).contains(httpStatus==null?0:httpStatus)
                    || httpStatus!=null && (httpStatus<100 || httpStatus>599))throw new IllegalArgumentException("Invalid cancellation observation");
        }
    }
    public record Receipt(Prepared prepared,String state,Observation observation,Long observedAt) {}
    private static final Collation BINARY=Collation.builder().locale("simple").build();
    private final MongoCollection<Document> operations;
    private final ReviewOrphanTargetResolver resolver;
    private final ReviewSnapshotArchive archive;
    private final Clock clock;
    private final long lifetimeMillis;
    public ReviewOrphanCancellationJournal(MongoTemplate mongo,ReviewOrphanTargetResolver resolver,ReviewSnapshotArchive archive,Clock clock,long lifetimeMillis) {
        if(lifetimeMillis<1000 || lifetimeMillis>120000)throw new IllegalArgumentException("Invalid cancellation lifetime");
        this.resolver=Objects.requireNonNull(resolver);this.archive=Objects.requireNonNull(archive);this.clock=Objects.requireNonNull(clock);this.lifetimeMillis=lifetimeMillis;
        operations=mongo.getCollection(COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY)
                .withWriteConcern(WriteConcern.MAJORITY.withJournal(true).withWTimeout(5,TimeUnit.SECONDS)).withTimeout(5000,TimeUnit.MILLISECONDS);
    }
    public record Preview(String isolationId,String projectIdType,String projectId,int versionIndex,String versionId,
                          String requestId,String jobId,String beforeSha256,String targetSha256,String artifactSha256) {}
    /** Original archived target only; does not prepare an operation or contact the remote service. */
    public Preview preview(String isolationId,String actor,BooleanSupplier permitted) {
        permission(permitted);if(!uuid(isolationId))throw new IllegalArgumentException("Invalid cancellation identity");
        var original=resolver.resolve(isolationId,actor);permission(permitted);
        boolean objectId=original.projectId() instanceof org.bson.types.ObjectId;
        var binding=original.binding();
        var result=new Preview(original.isolationId(),objectId?"OBJECT_ID":"STRING",
                objectId?((org.bson.types.ObjectId)original.projectId()).toHexString():(String)original.projectId(),
                original.versionIndex(),binding.versionId(),binding.requestId(),binding.jobId(),original.beforeSha256(),
                digest(bytes(target(original))),binding.artifactSha256());
        permission(permitted);return result;
    }
    public Prepared prepare(String isolationId,String actor,BooleanSupplier permitted) {
        permission(permitted);var target=resolver.resolve(isolationId,actor);permission(permitted);
        var stored=read(isolationId);
        var retained=archive.find(archiveId(isolationId));permission(permitted);
        if(retained==null) {
            if(stored!=null)throw unavailable();
            long now=clock.millis();var candidate=new ReviewSnapshotArchive.Snapshot(archiveId(isolationId),target.projectId(),target.versionIndex(),actor,
                    ReviewSnapshotArchive.Action.CANCEL_REMOTE_REVIEW,now,Math.addExact(now,lifetimeMillis),bytes(target(target)));
            permission(permitted);
            try {retained=archive.retain(candidate);}
            catch(RuntimeException uncertain) {retained=archive.find(candidate.id());if(retained==null)throw uncertain;}
        }
        permission(permitted);validateArchive(retained,actor,target);
        if(stored==null) {
            var intent=intent(actor,target,retained.createdAt(),retained.expiresAt());
            var candidate=new Document("_id",isolationId).append("intent",intent).append("state","PREPARED");
            permission(permitted);
            try {ReviewRepairIo.collection(operations).insertOne(candidate);}
            catch(MongoException uncertain) { /* Read back the original immutable intent; never replace it. */ }
            stored=read(isolationId);
        }
        permission(permitted);var prepared=authenticate(stored,actor,target);
        if(clock.millis()<prepared.createdAt() || clock.millis()>=prepared.expiresAt())throw unavailable();
        permission(permitted);return prepared;
    }
    public Claim claim(Prepared prepared,String actor,BooleanSupplier permitted) {
        permission(permitted);Objects.requireNonNull(prepared);
        var target=resolver.resolve(prepared.isolationId(),actor);permission(permitted);var stored=read(prepared.isolationId());
        if(!prepared.equals(authenticate(stored,actor,target)))throw unavailable();
        if(!"PREPARED".equals(stored.get("state")))return null;
        String token=UUID.randomUUID().toString();
        var predicate=new Document("$and",List.of(new Document("$eq",List.of("$$ROOT",literal(stored))),
                new Document("$eq",List.of(new Document("$type","$intent.createdAt"),"long")),
                new Document("$eq",List.of(new Document("$type","$intent.expiresAt"),"long")),
                new Document("$eq",List.of(new Document("$type","$intent.target.versionIndex"),"int")),
                new Document("$eq",List.of(new Document("$type","$intent.target.binding.attempt"),"int")),
                new Document("$gte",List.of("$$NOW",new Date(prepared.createdAt()))),new Document("$lt",List.of("$$NOW",new Date(prepared.expiresAt())))));
        var query=new Document("_id",prepared.isolationId()).append("$expr",predicate);
        permission(permitted);
        try {
            var armed=ReviewRepairIo.collection(operations).findOneAndUpdate(query,List.of(new Document("$set",new Document("state","EXECUTING").append("token",literal(token)).append("startedAt","$$NOW"))),
                    new FindOneAndUpdateOptions().collation(BINARY).returnDocument(ReturnDocument.AFTER));
            permission(permitted);
            return owned(armed,stored,token)?new Claim(prepared,token,target):null;
        }catch(MongoException uncertain) {
            permission(permitted);var armed=read(prepared.isolationId());permission(permitted);
            if(owned(armed,stored,token))return new Claim(prepared,token,target);
            throw unavailable();
        }
    }
    /** Returns the database-time budget once, immediately before the HTTP subscription. */
    public long beginDispatch(Claim claim,String actor,BooleanSupplier permitted) {
        permission(permitted);var stored=ownedRecord(claim,actor);permission(permitted);
        if(!"EXECUTING".equals(stored.get("state")))return 0;
        var query=exact(stored).append("$expr",new Document("$and",List.of(exactExpression(stored),
                new Document("$gte",List.of("$$NOW",new Date(claim.prepared().createdAt()))),new Document("$lt",List.of("$$NOW",new Date(claim.prepared().expiresAt()))))));
        permission(permitted);
        // A lost acknowledgement is deliberately not recovered into dispatch authority here.
        var dispatched=ReviewRepairIo.collection(operations).findOneAndUpdate(query,List.of(new Document("$set",new Document("state","DISPATCHING").append("dispatchAt","$$NOW"))),
                new FindOneAndUpdateOptions().collation(BINARY).returnDocument(ReturnDocument.AFTER));
        permission(permitted);if(dispatched==null)return 0;
        if(!claim.prepared().equals(authenticate(dispatched,actor,claim.target())))throw unavailable();
        return claim.prepared().expiresAt()-dispatched.getDate("dispatchAt").getTime();
    }
    public Receipt record(Claim claim,String actor,Observation observation,BooleanSupplier permitted) {
        permission(permitted);var stored=ownedRecord(claim,actor);validateObservation(observation,claim.target());permission(permitted);
        if(!"DISPATCHING".equals(stored.get("state")))return receipt(claim.prepared(),actor,permitted);
        var fields=new Document("state","UNKNOWN".equals(observation.kind())?"UNKNOWN":"OBSERVED")
                .append("observation",literal(observationDocument(observation))).append("finishedAt","$$NOW");
        permission(permitted);
        try {ReviewRepairIo.collection(operations).updateOne(exact(stored),List.of(new Document("$set",fields)),new UpdateOptions().collation(BINARY));}
        catch(MongoException uncertain) { /* Receipt read-back decides whether the observation was persisted. */ }
        return receipt(claim.prepared(),actor,permitted);
    }
    public Receipt receipt(Prepared prepared,String actor,BooleanSupplier permitted) {
        permission(permitted);Objects.requireNonNull(prepared);
        var recovered=recover(prepared.isolationId(),actor,permitted);
        if(!prepared.equals(recovered.prepared()))throw unavailable();
        permission(permitted);return recovered;
    }
    /** Read-only recovery of the original signed intent, including after expiry. */
    public Receipt recover(String isolationId,String actor,BooleanSupplier permitted) {
        permission(permitted);if(!uuid(isolationId))throw new IllegalArgumentException("Invalid cancellation identity");
        var target=resolver.resolve(isolationId,actor);permission(permitted);var stored=read(isolationId);
        var prepared=authenticate(stored,actor,target);
        String state=stored.getString("state");Observation observation=stored.containsKey("observation")?observation(stored.get("observation",Document.class)):null;
        permission(permitted);return new Receipt(prepared,state,observation,stored.containsKey("finishedAt")?stored.getDate("finishedAt").getTime():null);
    }
    /** Original target for observation only; this never returns or restores an execution token. */
    public ReviewOrphanTargetResolver.Target reconciliationTarget(Prepared prepared,String actor,BooleanSupplier permitted) {
        permission(permitted);var target=resolver.resolve(prepared.isolationId(),actor);permission(permitted);var stored=read(prepared.isolationId());
        if(!prepared.equals(authenticate(stored,actor,target)) || "PREPARED".equals(stored.get("state")))throw unavailable();
        permission(permitted);return target;
    }
    private Document ownedRecord(Claim claim,String actor) {
        if(claim==null || !uuid(claim.token()))throw unavailable();
        var target=resolver.resolve(claim.prepared().isolationId(),actor);var stored=read(claim.prepared().isolationId());
        if(!target.equals(claim.target()) || !claim.prepared().equals(authenticate(stored,actor,target)) || !claim.token().equals(stored.get("token")))throw unavailable();
        return stored;
    }
    private static Document exact(Document stored){return new Document("_id",stored.get("_id")).append("$expr",exactExpression(stored));}
    private static Document exactExpression(Document stored) {
        return new Document("$and",List.of(new Document("$eq",List.of("$$ROOT",literal(stored))),
                new Document("$eq",List.of(new Document("$type","$intent.createdAt"),"long")),new Document("$eq",List.of(new Document("$type","$intent.expiresAt"),"long")),
                new Document("$eq",List.of(new Document("$type","$intent.target.versionIndex"),"int")),new Document("$eq",List.of(new Document("$type","$intent.target.binding.attempt"),"int"))));
    }
    static Document observationDocument(Observation observation) {
        var status=observation.status();Document encoded=status==null?null:new Document("jobId",status.jobId()).append("state",status.state()).append("artifactRetained",status.artifactRetained())
                .append("createdAt",status.createdAt()).append("expiresAt",status.expiresAt()).append("workState",status.workState());
        return new Document("kind",observation.kind()).append("status",encoded).append("httpStatus",observation.httpStatus());
    }
    static Observation observation(Document doc) {
        if(doc==null || doc.size()!=3)throw unavailable();var status=doc.get("status",Document.class);RemoteReviewClient.Status parsed=null;
        if(status!=null) {
            if(status.size()!=6 || !(status.get("artifactRetained") instanceof Boolean) || !(status.get("createdAt") instanceof Long) || !(status.get("expiresAt") instanceof Long))throw unavailable();
            parsed=new RemoteReviewClient.Status(status.getString("jobId"),status.getString("state"),status.getBoolean("artifactRetained"),status.getLong("createdAt"),status.getLong("expiresAt"),status.getString("workState"));
        }
        var result=new Observation(doc.getString("kind"),parsed,doc.getInteger("httpStatus"));if(!Arrays.equals(bytes(doc),bytes(observationDocument(result))))throw unavailable();return result;
    }
    static void validateObservation(Observation observation,ReviewOrphanTargetResolver.Target target) {
        Objects.requireNonNull(observation);var status=observation.status();if(status==null)return;
        if(!target.binding().jobId().equals(status.jobId()) || !Set.of("QUEUED","RUNNING","COMPLETED","CANCELLED","EXPIRED","HELD","UPLOADING","AWAITING_UPLOAD").contains(Objects.toString(status.state(),""))
                || status.createdAt()<=0 || status.expiresAt()<=status.createdAt() || status.workState()!=null && status.workState().length()>128
                || Set.of("QUEUED","RUNNING","COMPLETED","HELD").contains(status.state()) && !status.artifactRetained())throw unavailable();
    }
    private Prepared authenticate(Document stored,String actor,ReviewOrphanTargetResolver.Target target) {
        try {
            if(stored==null || !target.isolationId().equals(stored.get("_id")))throw unavailable();
            var intent=stored.get("intent",Document.class);
            if(intent==null || !(intent.get("createdAt") instanceof Long created) || !(intent.get("expiresAt") instanceof Long expires)
                    || created<1 || expires<=created || expires-created>120000)throw unavailable();
            var retained=archive.load(archiveId(target.isolationId()));validateArchive(retained,actor,target);
            if(created!=retained.createdAt() || expires!=retained.expiresAt()
                    || !Arrays.equals(bytes(intent),bytes(intent(actor,target,created,expires))))throw unavailable();
            String state=stored.getString("state");
            if("PREPARED".equals(state)) {if(stored.size()!=3)throw unavailable();}
            else {
                if(!Set.of("EXECUTING","DISPATCHING","OBSERVED","UNKNOWN").contains(state) || !(stored.get("token") instanceof String token) || !uuid(token)
                        || !(stored.get("startedAt") instanceof Date started) || started.getTime()<created || started.getTime()>=expires)throw unavailable();
                if("EXECUTING".equals(state)) {if(stored.size()!=5)throw unavailable();}
                else {
                    if(!(stored.get("dispatchAt") instanceof Date dispatched) || dispatched.before(started) || dispatched.getTime()>=expires)throw unavailable();
                    if("DISPATCHING".equals(state)) {if(stored.size()!=6)throw unavailable();}
                    else {
                        if(stored.size()!=8 || !(stored.get("finishedAt") instanceof Date finished) || finished.before(dispatched))throw unavailable();
                        var observation=observation(stored.get("observation",Document.class));validateObservation(observation,target);
                        if("UNKNOWN".equals(state)!= "UNKNOWN".equals(observation.kind()))throw unavailable();
                    }
                }
            }
            return new Prepared(target.isolationId(),digest(bytes(target(target))),created,expires);
        }catch(RuntimeException invalid){throw unavailable();}
    }
    private static String archiveId(String isolationId) {
        if(!uuid(isolationId))throw unavailable();
        byte[] hash=HexFormat.of().parseHex(digest(("warden-orphan-cancellation-intent-v1\0"+isolationId).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        hash[6]=(byte)((hash[6]&15)|128);hash[8]=(byte)((hash[8]&63)|128);
        var buffer=java.nio.ByteBuffer.wrap(hash);return new UUID(buffer.getLong(),buffer.getLong()).toString();
    }
    private static void validateArchive(ReviewSnapshotArchive.Snapshot retained,String actor,ReviewOrphanTargetResolver.Target target) {
        if(retained==null || !archiveId(target.isolationId()).equals(retained.id()) || !actor.equals(retained.actorId())
                || retained.action()!=ReviewSnapshotArchive.Action.CANCEL_REMOTE_REVIEW || !target.projectId().equals(retained.projectId())
                || target.versionIndex()!=retained.versionIndex() || retained.expiresAt()-retained.createdAt()>120000
                || !Arrays.equals(retained.versionBytes(),bytes(target(target))))throw unavailable();
    }
    private static boolean owned(Document armed,Document before,String token) {
        if(armed==null || armed.size()!=5 || !(armed.get("startedAt") instanceof Date))return false;
        var expected=new Document(before).append("state","EXECUTING").append("token",token).append("startedAt",armed.get("startedAt"));
        return Arrays.equals(bytes(armed),bytes(expected));
    }
    private Document read(String id){return ReviewRepairIo.collection(operations).find(new Document("_id",id)).collation(BINARY).maxTime(5,TimeUnit.SECONDS).first();}
    private static Document intent(String actor,ReviewOrphanTargetResolver.Target target,long created,long expires) {
        return new Document("action","CANCEL_REMOTE_REVIEW").append("actor",actor).append("target",target(target)).append("createdAt",created).append("expiresAt",expires);
    }
    private static Document target(ReviewOrphanTargetResolver.Target target) {
        var b=target.binding();if(b==null || b.origin()==null || b.jobId()==null)throw unavailable();
        var binding=new Document("projectId",b.projectId()).append("versionId",b.versionId()).append("requestId",b.requestId()).append("attempt",b.attempt())
                .append("filePath",b.filePath()).append("artifactSha256",b.artifactSha256()).append("contextSha256",b.contextSha256()).append("policyVersion",b.policyVersion())
                .append("reviewConfigSha256",b.reviewConfigSha256()).append("jobId",b.jobId()).append("manualRescan",b.manualRescan())
                .append("origin",new Document("deploymentId",b.origin().deploymentId()).append("callerScope",b.origin().callerScope()));
        return new Document("isolationId",target.isolationId()).append("projectId",target.projectId()).append("versionIndex",target.versionIndex())
                .append("beforeSha256",target.beforeSha256()).append("binding",binding);
    }
    private static byte[] bytes(Document document){var buffer=new RawBsonDocument(document,new DocumentCodec()).getByteBuffer().asNIO();byte[] result=new byte[buffer.remaining()];buffer.get(result);return result;}
    private static String digest(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception invalid){throw unavailable();}}
    private static Document literal(Object value){return new Document("$literal",value);}
    private static boolean uuid(String value){return value!=null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    private static void permission(BooleanSupplier permitted){if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Cancellation is not permitted");}
    private static IllegalStateException unavailable(){return new IllegalStateException("Cancellation intent is unavailable or inconsistent");}
}
