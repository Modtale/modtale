package net.modtale.service.admin.review;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Signed admission decision only. It does not dispatch work or resolve any security finding. */
public final class ReviewReplacementActivationDecision {
    public record Prepared(String id,String replacementId,String observationId,String observationSha256,String heldSha256,
                           String rule,boolean acknowledgedUncertainty,long createdAt,long expiresAt) {}
    record Context(ReviewSnapshotArchive.Snapshot decision,Document admission,net.modtale.model.project.RemoteReviewBinding replacement) {}
    private record Recovered(Prepared prepared,ReviewSnapshotArchive.Snapshot snapshot) {}
    private final ReviewSnapshotArchive archive;
    private final ReviewReplacementPreparation preparation;
    private final ReviewReplacementExecutor executor;
    private final ReviewReplacementJobAccounting accounting;
    private final RawReviewSnapshotReader reader;
    private final MongoCollection<Document> admissions;
    private final Clock clock;
    private final long lifetimeMillis;
    public ReviewReplacementActivationDecision(MongoTemplate mongo,ReviewSnapshotArchive archive,ReviewReplacementPreparation preparation,
            ReviewReplacementExecutor executor,ReviewReplacementJobAccounting accounting,RawReviewSnapshotReader reader,Clock clock,long lifetimeMillis) {
        if(lifetimeMillis<1000 || lifetimeMillis>120000)throw new IllegalArgumentException("Invalid activation lifetime");
        this.archive=Objects.requireNonNull(archive);this.preparation=Objects.requireNonNull(preparation);this.executor=Objects.requireNonNull(executor);
        this.accounting=Objects.requireNonNull(accounting);this.reader=Objects.requireNonNull(reader);this.clock=Objects.requireNonNull(clock);this.lifetimeMillis=lifetimeMillis;
        admissions=mongo.getCollection(ReviewReplacementExecutor.ADMISSIONS).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY);
    }

    public Prepared prepare(String id,ReviewReplacementPreparation.Prepared replacement,String observationId,String actor,
                            boolean acknowledgeUncertainty,BooleanSupplier permitted) {
        permission(permitted);
        if(!uuid(id) || !uuid(observationId) || actor==null || actor.isBlank() || actor.length()>256)throw new IllegalArgumentException("Invalid activation identity");
        var original=archive.load(replacement.id());permission(permitted);
        // The acting moderator may differ from the historical author of the staged proposal.
        var recovered=preparation.recoverEvidence(replacement.id(),original.actorId(),permitted);
        if(!replacement.equals(recovered.prepared()))throw conflict();
        var staged=executor.receipt(replacement,original.actorId(),permitted);
        if(!"APPLIED".equals(staged.state()))throw conflict();
        var source=recovered.before();
        var captured=reader.capture(source.projectId(),source.versionIndex(),replacement.replacement().versionId());permission(permitted);
        if(!captured.sha256().equals(staged.afterSha256()))throw conflict();
        var admission=readAdmission(replacement.id());permission(permitted);
        var expected=new Document("_id",replacement.id()).append("beforeArchiveId",replacement.beforeArchiveId()).append("beforeSha256",replacement.beforeSha256())
                .append("afterSha256",staged.afterSha256()).append("actor",original.actorId()).append("projectId",source.projectId())
                .append("versionIndex",source.versionIndex()).append("requestId",replacement.replacement().requestId()).append("state","HELD");
        if(admission==null || !Arrays.equals(bytes(expected),bytes(admission)))throw conflict();
        var observation=accounting.receipt(observationId,replacement,original.actorId(),permitted);
        if(!Set.of("OBSERVED","UNKNOWN").contains(observation.state()))throw new IllegalStateException("A completed status observation is required before activation");
        var observationBody=observation(observation);
        boolean completed=completed(observation);
        if(!completed && !acknowledgeUncertainty)throw new IllegalStateException("The original job remains uncertain; acknowledge possible duplicate work before activation");
        String rule=completed?"ORIGINAL_COMPLETED":"ACKNOWLEDGED_UNCERTAINTY";
        var payload=new Document("schema",1).append("replacementId",replacement.id()).append("admission",admission)
                .append("observation",observationBody).append("heldSha256",captured.sha256()).append("rule",rule).append("acknowledgedUncertainty",acknowledgeUncertainty);
        var existing=archive.find(id);permission(permitted);
        long created=existing==null?clock.millis():existing.createdAt(),expires=existing==null?Math.addExact(created,lifetimeMillis):existing.expiresAt();
        live(created,expires);
        var candidate=new ReviewSnapshotArchive.Snapshot(id,source.projectId(),source.versionIndex(),actor,ReviewSnapshotArchive.Action.REPLACEMENT_ACTIVATION,created,expires,bytes(payload));
        permission(permitted);archive.retain(candidate);permission(permitted);
        if(!reader.isCurrent(captured) || !Arrays.equals(bytes(admission),bytes(readAdmission(replacement.id())))
                || !observation.equals(accounting.receipt(observationId,replacement,original.actorId(),permitted)))throw conflict();
        var result=recover(id,actor,permitted);live(result.createdAt(),result.expiresAt());return result;
    }

    /** Historical read-back only; expiry never extends and recovery does not activate the admission. */
    public Prepared recover(String id,String actor,BooleanSupplier permitted) {
        return recoverEvidence(id,actor,permitted).prepared();
    }
    private Recovered recoverEvidence(String id,String actor,BooleanSupplier permitted) {
        permission(permitted);var stored=archive.load(id);permission(permitted);
        if(stored.action()!=ReviewSnapshotArchive.Action.REPLACEMENT_ACTIVATION || !stored.actorId().equals(actor)
                || stored.expiresAt()-stored.createdAt()>120000)throw conflict();
        var payload=new RawBsonDocument(stored.versionBytes()).decode(new DocumentCodec());
        if(payload.size()!=7 || !Integer.valueOf(1).equals(payload.get("schema")) || !(payload.get("acknowledgedUncertainty") instanceof Boolean ack)
                || !(payload.get("heldSha256") instanceof String held) || !held.matches("[0-9a-f]{64}"))throw conflict();
        String replacement=payload.getString("replacementId"),rule=payload.getString("rule");var observation=payload.get("observation",Document.class);
        if(!uuid(replacement) || observation==null || !uuid(observation.getString("id")) || !replacement.equals(observation.get("replacementId"))
                || !Set.of("ORIGINAL_COMPLETED","ACKNOWLEDGED_UNCERTAINTY").contains(Objects.toString(rule,""))
                || "ACKNOWLEDGED_UNCERTAINTY".equals(rule) && !ack)throw conflict();
        if(!rule.equals(completed(observation(observation))?"ORIGINAL_COMPLETED":"ACKNOWLEDGED_UNCERTAINTY"))throw conflict();
        var admission=payload.get("admission",Document.class);
        if(admission==null || !replacement.equals(admission.get("_id")) || !stored.projectId().equals(admission.get("projectId"))
                || !Integer.valueOf(stored.versionIndex()).equals(admission.get("versionIndex")) || !held.equals(admission.get("afterSha256"))
                || !"HELD".equals(admission.get("state")))throw conflict();
        permission(permitted);return new Recovered(new Prepared(id,replacement,observation.getString("id"),digest(bytes(observation)),held,rule,ack,stored.createdAt(),stored.expiresAt()),stored);
    }
    private Document readAdmission(String id) {
        return ReviewRepairIo.collection(admissions).find(new Document("_id",id)).collation(Collation.builder().locale("simple").build()).maxTime(5,TimeUnit.SECONDS).first();
    }
    Context verifyCurrent(Prepared expected,String actor,BooleanSupplier permitted) {
        if(!expected.equals(recover(expected.id(),actor,permitted)))throw conflict();
        var original=archive.load(expected.replacementId());
        var replacement=preparation.recover(original.id(),original.actorId(),permitted);
        if(!expected.equals(prepare(expected.id(),replacement,expected.observationId(),actor,expected.acknowledgedUncertainty(),permitted)))throw conflict();
        var verified=recoverEvidence(expected.id(),actor,permitted);
        if(!expected.equals(verified.prepared()))throw conflict();
        var source=verified.snapshot();
        var payload=new RawBsonDocument(source.versionBytes()).decode(new DocumentCodec());
        return new Context(source,payload.get("admission",Document.class),replacement.replacement());
    }
    private static boolean completed(ReviewReplacementJobAccounting.Receipt receipt) {
        var observation=receipt.observation();return "OBSERVED".equals(receipt.state()) && observation!=null && "REMOTE_STATUS".equals(observation.kind())
                && "COMPLETED".equals(observation.status().state()) && "COMPLETED".equals(observation.status().workState());
    }
    private static Document observation(ReviewReplacementJobAccounting.Receipt receipt) {
        return new Document("id",receipt.id()).append("replacementId",receipt.replacementId()).append("state",receipt.state())
                .append("observation",receipt.observation()==null?null:ReviewOrphanCancellationJournal.observationDocument(receipt.observation())).append("receivedAt",receipt.receivedAt());
    }
    private static ReviewReplacementJobAccounting.Receipt observation(Document body) {
        if(body.size()!=5 || !Set.of("RESERVED","READING","OBSERVED","UNKNOWN").contains(Objects.toString(body.get("state"),""))
                || body.get("receivedAt")!=null && !(body.get("receivedAt") instanceof Long))throw conflict();
        var decoded=body.get("observation")==null?null:ReviewOrphanCancellationJournal.observation(body.get("observation",Document.class));
        boolean finished=Set.of("OBSERVED","UNKNOWN").contains(body.getString("state"));
        if(finished!=(decoded!=null) || finished!=(body.get("receivedAt")!=null)
                || finished && (body.getLong("receivedAt")<=0 || "UNKNOWN".equals(body.getString("state"))!="UNKNOWN".equals(decoded.kind())))throw conflict();
        var result=new ReviewReplacementJobAccounting.Receipt(body.getString("id"),body.getString("replacementId"),body.getString("state"),decoded,body.getLong("receivedAt"));
        if(!Arrays.equals(bytes(body),bytes(observation(result))))throw conflict();return result;
    }
    private static byte[] bytes(Document doc) {
        if(doc==null)throw conflict();var buffer=new RawBsonDocument(doc,new DocumentCodec()).getByteBuffer().asNIO();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;
    }
    private static String digest(byte[] bytes) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private void live(long created,long expires){long now=clock.millis();if(now<created || now>=expires)throw conflict();}
    private static boolean uuid(String id){return id!=null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    private static void permission(BooleanSupplier permitted){if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Activation preparation is not permitted");}
    private static IllegalStateException conflict(){return new IllegalStateException("Activation decision changed or is unavailable");}
}
