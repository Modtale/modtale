package net.modtale.service.admin.review;

import net.modtale.model.project.RemoteReviewBinding;
import net.modtale.model.project.RemoteReviewOrigin;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Signed proposal and evidence retention only; no queue admission, project mutation or remote requests. */
public final class ReviewReplacementPreparation {
    public record Configuration(String policyVersion,String reviewConfigSha256,RemoteReviewOrigin origin) {
        public Configuration {
            if(policyVersion==null || !policyVersion.matches("warden-3\\.0\\.0:[0-9a-f]{64}")
                    || reviewConfigSha256==null || !reviewConfigSha256.matches("[0-9a-f]{64}") || origin==null)
                throw new IllegalArgumentException("Invalid replacement configuration");
        }
    }
    public record Request(String id,Object projectId,int versionIndex,String versionId,String expectedSha256,String actorId,Configuration configuration) {
        public Request {
            new ReviewRepairPreparation.Request(id,projectId,versionIndex,versionId,expectedSha256,actorId,ReviewSnapshotArchive.Action.REPLACE_REVIEW);
            Objects.requireNonNull(configuration);
        }
    }
    public record Prepared(String id,String beforeArchiveId,String beforeSha256,long createdAt,long expiresAt,
                           RemoteReviewBinding replacement,String isolationId,String isolationBeforeSha256) {}
    public record Recovered(Prepared prepared,ReviewSnapshotArchive.Snapshot before) {}
    private final ReviewReplacementEvidenceReader evidence;
    private final ReviewRemoteTargetReader targets;
    private final RawReviewSnapshotReader snapshots;
    private final ReviewSnapshotArchive archive;
    private final Clock clock;
    private final long lifetimeMillis;

    public ReviewReplacementPreparation(ReviewReplacementEvidenceReader evidence,ReviewRemoteTargetReader targets,
            RawReviewSnapshotReader snapshots,ReviewSnapshotArchive archive,Clock clock,long lifetimeMillis) {
        if(lifetimeMillis<1000 || lifetimeMillis>120000)throw new IllegalArgumentException("Invalid replacement lifetime");
        this.evidence=Objects.requireNonNull(evidence);this.targets=Objects.requireNonNull(targets);
        this.snapshots=Objects.requireNonNull(snapshots);this.archive=Objects.requireNonNull(archive);
        this.clock=Objects.requireNonNull(clock);this.lifetimeMillis=lifetimeMillis;
    }

    public Prepared prepare(Request request,BooleanSupplier permitted) {
        permission(permitted);Objects.requireNonNull(request);
        var captured=evidence.capture(request.projectId(),request.versionIndex(),request.versionId(),permitted);
        if(!captured.current().snapshot().sha256().equals(request.expectedSha256()))throw conflict();
        // Detect attempt exhaustion before retaining anything; arithmetic must never wrap to a reused attempt.
        replacement(request.id(),captured.current().binding(),request.configuration());
        permission(permitted);
        var before=archive.find(beforeId(request.id()));
        if(before==null) {
            long now=clock.millis();
            var candidate=captured.current().snapshot().forArchive(beforeId(request.id()),request.actorId(),
                    ReviewSnapshotArchive.Action.REPLACE_REVIEW,now,Math.addExact(now,lifetimeMillis));
            permission(permitted);
            try {before=archive.retain(candidate);}
            catch(RuntimeException uncertain) {before=archive.find(candidate.id());if(before==null)throw uncertain;}
        }
        permission(permitted);
        if(!before.actorId().equals(request.actorId()) || before.action()!=ReviewSnapshotArchive.Action.REPLACE_REVIEW
                || !before.projectId().equals(request.projectId()) || before.versionIndex()!=request.versionIndex()
                || !Arrays.equals(before.versionBytes(),captured.current().snapshot().versionBytes()))throw conflict();
        live(before.createdAt(),before.expiresAt());
        var payload=new Document("schema",1).append("beforeArchiveId",before.id()).append("beforeSha256",request.expectedSha256())
                .append("policyVersion",request.configuration().policyVersion()).append("reviewConfigSha256",request.configuration().reviewConfigSha256())
                .append("deploymentId",request.configuration().origin().deploymentId()).append("callerScope",request.configuration().origin().callerScope());
        var intent=new ReviewSnapshotArchive.Snapshot(request.id(),request.projectId(),request.versionIndex(),request.actorId(),
                ReviewSnapshotArchive.Action.REPLACEMENT_INTENT,before.createdAt(),before.expiresAt(),bytes(payload));
        permission(permitted);
        // A fixed ID cannot select different configuration on retry, including a race with another preparation.
        archive.retain(intent);
        permission(permitted);
        var prepared=recover(request.id(),request.actorId(),permitted);
        if(!snapshots.isCurrent(captured.current().snapshot()))throw conflict();
        permission(permitted);live(prepared.createdAt(),prepared.expiresAt());return prepared;
    }

    /** Recovery is read-only and remains possible after expiry or changes to the current version. */
    public Prepared recover(String id,String actor,BooleanSupplier permitted) {
        return recoverEvidence(id,actor,permitted).prepared();
    }
    public Recovered recoverEvidence(String id,String actor,BooleanSupplier permitted) {
        return recoverEvidence(archive,targets,id,actor,permitted);
    }
    static Recovered recoverEvidence(ReviewSnapshotArchive archive,ReviewRemoteTargetReader targets,String id,String actor,BooleanSupplier permitted) {
        permission(permitted);
        var intent=archive.load(id);permission(permitted);
        if(intent.action()!=ReviewSnapshotArchive.Action.REPLACEMENT_INTENT || !intent.actorId().equals(actor)
                || intent.expiresAt()-intent.createdAt()>120000)throw conflict();
        var payload=new RawBsonDocument(intent.versionBytes()).decode(new DocumentCodec());
        if(payload.size()!=7 || !Integer.valueOf(1).equals(payload.get("schema"))
                || !beforeId(id).equals(payload.get("beforeArchiveId")))throw conflict();
        var before=archive.load(beforeId(id));permission(permitted);
        if(before.action()!=ReviewSnapshotArchive.Action.REPLACE_REVIEW || !before.actorId().equals(actor)
                || !before.projectId().equals(intent.projectId()) || before.versionIndex()!=intent.versionIndex()
                || before.createdAt()!=intent.createdAt() || before.expiresAt()!=intent.expiresAt()
                || !digest(before.versionBytes()).equals(payload.get("beforeSha256")))throw conflict();
        var version=new RawBsonDocument(before.versionBytes()).decode(new DocumentCodec());
        var old=targets.validate(before.projectId(),version);
        var configuration=new Configuration(payload.getString("policyVersion"),payload.getString("reviewConfigSha256"),
                new RemoteReviewOrigin(payload.getString("deploymentId"),payload.getString("callerScope")));
        var isolation=version.get("reviewIsolation",Document.class);
        var result=new Prepared(id,before.id(),payload.getString("beforeSha256"),intent.createdAt(),intent.expiresAt(),
                replacement(id,old,configuration),isolation==null?null:isolation.getString("operationId"),
                isolation==null?null:isolation.getString("beforeSha256"));
        permission(permitted);return new Recovered(result,before);
    }

    private static RemoteReviewBinding replacement(String id,RemoteReviewBinding old,Configuration config) {
        String requestId=derived("request",id);
        if(requestId.equals(old.requestId()))throw conflict();
        return new RemoteReviewBinding(old.projectId(),old.versionId(),requestId,Math.addExact(old.attempt(),1),
                old.filePath(),old.artifactSha256(),old.contextSha256(),config.policyVersion(),config.reviewConfigSha256(),null,true,config.origin());
    }
    private static String beforeId(String id){return derived("before",id);}
    private static String derived(String purpose,String id) {
        return UUID.nameUUIDFromBytes(("modtale-review-replacement-v1:"+purpose+":"+id).getBytes(StandardCharsets.UTF_8)).toString();
    }
    private static byte[] bytes(Document document) {
        var buffer=new RawBsonDocument(document,new DocumentCodec()).getByteBuffer().asNIO();
        byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;
    }
    private static String digest(byte[] bytes) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private void live(long created,long expires) {
        long now=clock.millis();if(now<created || now>=expires)throw conflict();
    }
    private static void permission(BooleanSupplier permitted) {
        if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Replacement preparation is not permitted");
    }
    private static IllegalStateException conflict(){return new IllegalStateException("Replacement preparation changed or is unavailable");}
}
