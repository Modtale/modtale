package net.modtale.service.admin.review;

import net.modtale.model.project.ProjectVersion;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Current evidence and authenticated isolation history; no replacement or remote-work authority. */
public final class ReviewReplacementEvidenceReader {
    public record Evidence(ReviewRemoteTargetReader.Captured current,ReviewOrphanTargetResolver.Target isolation,ReviewReplacementHistoryReader.Link previousReplacement) {}
    private final ReviewRemoteTargetReader current;
    private final ReviewOrphanTargetResolver orphans;
    private final ReviewReplacementHistoryReader history;

    public ReviewReplacementEvidenceReader(ReviewRemoteTargetReader current,ReviewOrphanTargetResolver orphans,ReviewReplacementHistoryReader history) {
        this.current=Objects.requireNonNull(current);this.orphans=Objects.requireNonNull(orphans);
        this.history=Objects.requireNonNull(history);
    }

    public Evidence capture(Object projectId,int versionIndex,String versionId,BooleanSupplier permitted) {
        permission(permitted);
        var captured=current.capture(projectId,versionIndex,versionId);
        permission(permitted);
        var version=new RawBsonDocument(captured.snapshot().versionBytes()).decode(new DocumentCodec());
        var scan=version.get("scanResult",Document.class);
        Object prior=version.get("reviewReplacement");
        if(prior==null)history.requireUntracked(captured.binding().requestId(),permitted);
        var previous=prior==null?null:history.verifyHead(projectId,versionIndex,captured.binding(),prior,permitted);
        Object reference=version.get("reviewIsolation");
        if(reference==null) {
            if("REMOTE_ISOLATED".equals(scan.get("scanState")))throw inconsistent();
            permission(permitted);return new Evidence(captured,null,previous);
        }
        ProjectVersion.ReviewIsolation isolation;
        try {
            if(!(reference instanceof Document raw) || !(raw.get("operationId") instanceof String id)
                    || !(raw.get("actorId") instanceof String actor) || !(raw.get("beforeSha256") instanceof String digest)
                    || !(raw.get("isolatedAt") instanceof java.util.Date date))throw inconsistent();
            isolation=new ProjectVersion.ReviewIsolation(id,actor,digest,date);
        } catch(RuntimeException invalid) {throw inconsistent();}
        // The saved actor selects historical evidence; only the caller's fresh permission grants access.
        var original=orphans.resolve(isolation.operationId(),isolation.actorId());
        permission(permitted);
        if(!original.projectId().equals(captured.snapshot().projectId())
                || original.versionIndex()!=captured.snapshot().versionIndex()
                || !original.beforeSha256().equals(isolation.beforeSha256())
                || !original.binding().equals(captured.binding()))throw inconsistent();
        permission(permitted);return new Evidence(captured,original,previous);
    }

    private static void permission(BooleanSupplier permitted) {
        if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Review evidence access is not permitted");
    }
    private static IllegalStateException inconsistent() {
        return new IllegalStateException("Retained isolation history is unavailable or inconsistent");
    }
}
