package net.modtale.service.admin.review;

import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Retains a proposed per-version owner mutation. Retention alone grants no mutation or review authority. */
public final class VersionMutationPreparation {
    public enum Mutation { CONTEXT_EDIT, SUBMISSION, REMOVAL, TARGET_REPLACEMENT }
    public record Request(String id,Object projectId,int beforeIndex,String versionId,String expectedSha256,String actor,
                          Mutation mutation,int afterIndex,byte[] proposedVersion) {
        public Request { proposedVersion=proposedVersion==null?null:proposedVersion.clone(); }
        @Override public byte[] proposedVersion(){return proposedVersion==null?null:proposedVersion.clone();}
        @Override public String toString(){return "VersionMutationRequest["+id+"]";}
    }
    public record Prepared(String id,String beforeArchiveId,String afterArchiveId,String beforeSha256,String afterSha256,
                           Mutation mutation,int afterIndex,Set<VersionReviewTransition.Change> changes,long createdAt,long expiresAt) {
        public Prepared {changes=Set.copyOf(changes);}
    }
    public record Recovered(Prepared prepared,ReviewSnapshotArchive.Snapshot before,ReviewSnapshotArchive.Snapshot after) {}
    private final RawReviewSnapshotReader reader;
    private final ReviewSnapshotArchive archive;
    private final Clock clock;
    private final long lifetime;
    public VersionMutationPreparation(RawReviewSnapshotReader reader,ReviewSnapshotArchive archive,Clock clock,long lifetime) {
        if(lifetime<1000 || lifetime>120000)throw new IllegalArgumentException("Invalid mutation preparation lifetime");
        this.reader=Objects.requireNonNull(reader);this.archive=Objects.requireNonNull(archive);this.clock=Objects.requireNonNull(clock);this.lifetime=lifetime;
    }
    public Prepared prepare(Request request,BooleanSupplier permitted) {
        permission(permitted);Objects.requireNonNull(request);
        if(!uuid(request.id()) || request.actor()==null || request.actor().isBlank() || request.actor().length()>256
                || request.mutation()==null || request.expectedSha256()==null || !request.expectedSha256().matches("[0-9a-f]{64}"))throw invalid();
        var captured=reader.capture(request.projectId(),request.beforeIndex(),request.versionId());permission(permitted);
        if(!captured.sha256().equals(request.expectedSha256()))throw invalid();
        var transition=classify(captured.versionBytes(),request.proposedVersion(),request.mutation(),request.afterIndex());
        if(!transition.requiresRetention())throw new IllegalArgumentException("This mutation does not require retained review history");
        String beforeId=derived(request.id(),"before"),afterId=request.proposedVersion()==null?null:derived(request.id(),"after");
        var existing=archive.find(beforeId);permission(permitted);
        long created=existing==null?clock.millis():existing.createdAt(),expires=existing==null?Math.addExact(created,lifetime):existing.expiresAt();
        live(created,expires);
        var before=captured.forArchive(beforeId,request.actor(),ReviewSnapshotArchive.Action.VERSION_MUTATION_BEFORE,created,expires);
        retain(before,permitted);
        if(afterId!=null)retain(new ReviewSnapshotArchive.Snapshot(afterId,request.projectId(),request.afterIndex(),request.actor(),
                ReviewSnapshotArchive.Action.VERSION_MUTATION_AFTER,created,expires,request.proposedVersion()),permitted);
        var payload=new Document("schema",1).append("beforeArchiveId",beforeId).append("afterArchiveId",afterId)
                .append("beforeSha256",captured.sha256()).append("afterSha256",transition.afterSha256())
                .append("mutation",request.mutation().name()).append("afterIndex",request.afterIndex());
        retain(new ReviewSnapshotArchive.Snapshot(request.id(),request.projectId(),request.beforeIndex(),request.actor(),
                ReviewSnapshotArchive.Action.VERSION_MUTATION_INTENT,created,expires,bytes(payload)),permitted);
        if(!reader.isCurrent(captured))throw invalid();permission(permitted);
        var result=recover(request.id(),request.actor(),permitted).prepared();live(result.createdAt(),result.expiresAt());return result;
    }
    /** Historical read-only recovery: never recaptures current state or refreshes expiry. */
    public Recovered recover(String id,String actor,BooleanSupplier permitted) {
        permission(permitted);var intent=archive.load(id);permission(permitted);
        if(intent.action()!=ReviewSnapshotArchive.Action.VERSION_MUTATION_INTENT || !intent.actorId().equals(actor)
                || intent.expiresAt()-intent.createdAt()>120000)throw invalid();
        var payload=decode(intent.versionBytes());
        if(payload.size()!=7 || !Integer.valueOf(1).equals(payload.get("schema")) || !(payload.get("afterIndex") instanceof Integer index))throw invalid();
        var mutation=Mutation.valueOf(payload.getString("mutation"));
        String beforeId=payload.getString("beforeArchiveId"),afterId=payload.getString("afterArchiveId");
        if(!derived(id,"before").equals(beforeId) || afterId!=null && !derived(id,"after").equals(afterId))throw invalid();
        var before=archive.load(beforeId);permission(permitted);
        sameHeader(intent,before,ReviewSnapshotArchive.Action.VERSION_MUTATION_BEFORE,intent.versionIndex());
        var after=afterId==null?null:archive.load(afterId);permission(permitted);
        if(after!=null)sameHeader(intent,after,ReviewSnapshotArchive.Action.VERSION_MUTATION_AFTER,index);
        var transition=classify(before.versionBytes(),after==null?null:after.versionBytes(),mutation,index);
        if(!transition.requiresRetention() || !Objects.equals(transition.beforeSha256(),payload.get("beforeSha256"))
                || !Objects.equals(transition.afterSha256(),payload.get("afterSha256")))throw invalid();
        permission(permitted);
        return new Recovered(new Prepared(id,beforeId,afterId,transition.beforeSha256(),transition.afterSha256(),mutation,index,
                transition.changes(),intent.createdAt(),intent.expiresAt()),before,after);
    }
    private void retain(ReviewSnapshotArchive.Snapshot candidate,BooleanSupplier permitted) {
        permission(permitted);
        try {archive.retain(candidate);}
        catch(RuntimeException uncertain) {
            var saved=archive.find(candidate.id());
            if(saved==null || !same(candidate,saved))throw uncertain;
        }
        permission(permitted);
    }
    private static VersionReviewTransition.Transition classify(byte[] old,byte[] next,Mutation mutation,int afterIndex) {
        if((mutation==Mutation.REMOVAL)!=(next==null) || (next==null?afterIndex!=-1:afterIndex<0 || afterIndex>16*1024*1024))throw invalid();
        var before=decode(old);var after=next==null?null:decode(next);
        if(after!=null && !Objects.equals(before.get("_id"),after.get("_id")))throw invalid();
        var changes=VersionReviewTransition.classify(List.of(before),after==null?List.of():List.of(after));
        if(changes.size()!=1)throw invalid();
        return changes.getFirst();
    }
    private static void sameHeader(ReviewSnapshotArchive.Snapshot intent,ReviewSnapshotArchive.Snapshot part,ReviewSnapshotArchive.Action action,int index) {
        if(part.action()!=action || !part.projectId().equals(intent.projectId()) || part.versionIndex()!=index || !part.actorId().equals(intent.actorId())
                || part.createdAt()!=intent.createdAt() || part.expiresAt()!=intent.expiresAt())throw invalid();
    }
    private static boolean same(ReviewSnapshotArchive.Snapshot a,ReviewSnapshotArchive.Snapshot b) {
        return a.id().equals(b.id()) && a.projectId().equals(b.projectId()) && a.versionIndex()==b.versionIndex() && a.actorId().equals(b.actorId())
                && a.action()==b.action() && a.createdAt()==b.createdAt() && a.expiresAt()==b.expiresAt() && Arrays.equals(a.versionBytes(),b.versionBytes());
    }
    private void live(long created,long expires){long now=clock.millis();if(now<created || now>=expires)throw invalid();}
    private static Document decode(byte[] value) {
        if(value==null || value.length<5 || value.length>ReviewSnapshotArchive.MAX_BYTES)throw invalid();
        var decoded=new RawBsonDocument(value).decode(new DocumentCodec());
        // Reject duplicate-field/trailing-byte representations instead of signing a lossy decoded proposal.
        if(!Arrays.equals(value,bytes(decoded)))throw invalid();
        return decoded;
    }
    private static byte[] bytes(Document value){var buffer=new RawBsonDocument(value,new DocumentCodec()).getByteBuffer().asNIO();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;}
    private static String derived(String id,String phase){return UUID.nameUUIDFromBytes(("version-mutation-1:"+id+":"+phase).getBytes(StandardCharsets.UTF_8)).toString();}
    private static boolean uuid(String id){return id!=null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    private static IllegalStateException invalid(){return new IllegalStateException("Version mutation evidence is unavailable or inconsistent");}
    private static void permission(BooleanSupplier permitted){if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Version mutation preparation is not permitted");}
}
