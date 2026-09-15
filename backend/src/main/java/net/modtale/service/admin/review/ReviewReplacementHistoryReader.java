package net.modtale.service.admin.review;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.RemoteReviewBinding;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** One authenticated history link per read; never recursively loads an unbounded replacement chain. */
public final class ReviewReplacementHistoryReader {
    public record Link(String operationId,String beforeArchiveId,String beforeSha256,String previousOperationId) {}
    private final ReviewSnapshotArchive archive;
    private final ReviewRemoteTargetReader targets;
    private final MongoCollection<Document> operations;
    private final ReviewReplacementAdmissionReader admissions;
    public ReviewReplacementHistoryReader(MongoTemplate mongo,ReviewSnapshotArchive archive,ReviewRemoteTargetReader targets) {
        this.archive=Objects.requireNonNull(archive);this.targets=Objects.requireNonNull(targets);
        operations=mongo.getCollection(ReviewRepairJournal.COLLECTION).withReadPreference(ReadPreference.primary()).withReadConcern(ReadConcern.MAJORITY);
        admissions=new ReviewReplacementAdmissionReader(mongo);
    }

    public void requireUntracked(String requestId,BooleanSupplier permitted) {
        if(admissions.hasRequest(requestId,permitted))throw inconsistent();
    }

    public Link verifyHead(Object projectId,int versionIndex,RemoteReviewBinding current,Object rawReference,BooleanSupplier permitted) {
        permission(permitted);
        var reference=reference(rawReference);
        var intent=archive.load(reference.operationId());permission(permitted);
        // The archived actor is historical attribution, not the identity granting access now.
        var recovered=ReviewReplacementPreparation.recoverEvidence(archive,targets,reference.operationId(),intent.actorId(),permitted);
        var prepared=recovered.prepared();var before=recovered.before();
        var expected=prepared.replacement();
        if(current.jobId()!=null)expected=expected.withJobId(current.jobId());
        if(!projectId.equals(before.projectId()) || versionIndex!=before.versionIndex()
                || !reference.beforeSha256().equals(prepared.beforeSha256()) || !reference.requestId().equals(prepared.replacement().requestId())
                || !expected.equals(current))throw inconsistent();
        var op=ReviewRepairIo.collection(operations).find(new Document("_id",before.id()))
                .collation(Collation.builder().locale("simple").build()).maxTime(5,TimeUnit.SECONDS).first();
        permission(permitted);
        if(op==null || !"APPLIED".equals(op.get("state")) || !"REPLACE_REVIEW".equals(op.get("action"))
                || !prepared.id().equals(op.get("replacementId")) || !before.actorId().equals(op.get("actor"))
                || !prepared.beforeSha256().equals(op.get("sha256")) || !Long.valueOf(prepared.createdAt()).equals(op.get("createdAt"))
                || !Long.valueOf(prepared.expiresAt()).equals(op.get("expiresAt")) || !(op.get("finishedAt") instanceof Date)
                || !(op.get("afterSha256") instanceof String after) || !after.matches("[0-9a-f]{64}"))throw inconsistent();
        var original=new RawBsonDocument(before.versionBytes()).decode(new DocumentCodec());
        Object previous=original.get("reviewReplacement");
        String previousId=previous==null?null:reference(previous).operationId();
        if(prepared.id().equals(previousId))throw inconsistent();
        permission(permitted);return new Link(prepared.id(),before.id(),prepared.beforeSha256(),previousId);
    }

    private static ProjectVersion.ReviewReplacement reference(Object value) {
        try {
            if(!(value instanceof Document raw) || raw.size()!=3 || !(raw.get("operationId") instanceof String id)
                    || !(raw.get("beforeSha256") instanceof String sha) || !(raw.get("requestId") instanceof String request))throw inconsistent();
            return new ProjectVersion.ReviewReplacement(id,sha,request);
        } catch(RuntimeException malformed) {throw inconsistent();}
    }
    private static void permission(BooleanSupplier permitted) {
        if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Replacement history access is not permitted");
    }
    private static IllegalStateException inconsistent(){return new IllegalStateException("Replacement history is unavailable or inconsistent");}
}
