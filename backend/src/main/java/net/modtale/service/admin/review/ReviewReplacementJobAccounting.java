package net.modtale.service.admin.review;

import net.modtale.service.security.scan.RemoteReviewClient;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Durable observations of the archived original job. Observation alone never activates a replacement. */
public final class ReviewReplacementJobAccounting implements AutoCloseable {
    public static final String COLLECTION="review_replacement_job_observations";
    public record Receipt(String id,String replacementId,String state,ReviewOrphanCancellationJournal.Observation observation,Long receivedAt) {}
    private final ReviewReplacementPreparation preparation;
    private final ReviewReplacementExecutor executor;
    private final ReviewRemoteTargetReader targets;
    private final ReviewRemoteStatusObserver observer;
    public ReviewReplacementJobAccounting(MongoTemplate mongo,ReviewReplacementPreparation preparation,ReviewReplacementExecutor executor,
            ReviewRemoteTargetReader targets,RemoteReviewClient client,int concurrency,Duration budget) {
        this.preparation=Objects.requireNonNull(preparation);this.executor=Objects.requireNonNull(executor);this.targets=Objects.requireNonNull(targets);
        observer=new ReviewRemoteStatusObserver(mongo,COLLECTION,client,concurrency,budget);
    }
    public Receipt check(String id,ReviewReplacementPreparation.Prepared replacement,String actor,BooleanSupplier permitted) {
        return receipt(replacement.id(),observer.check(id,allowed->target(replacement,actor,allowed),permitted));
    }
    public Receipt receipt(String id,ReviewReplacementPreparation.Prepared replacement,String actor,BooleanSupplier permitted) {
        return receipt(replacement.id(),observer.receipt(id,allowed->target(replacement,actor,allowed),permitted));
    }
    private ReviewRemoteStatusObserver.Target target(ReviewReplacementPreparation.Prepared replacement,String actor,BooleanSupplier permitted) {
        var recovered=preparation.recoverEvidence(replacement.id(),actor,permitted);
        if(!replacement.equals(recovered.prepared()) || !"APPLIED".equals(executor.receipt(replacement,actor,permitted).state()))
            throw new IllegalStateException("Replacement has not been staged with retained evidence");
        var before=recovered.before();var original=targets.validate(before.projectId(),new RawBsonDocument(before.versionBytes()).decode(new DocumentCodec()));
        var intent=new Document("actor",actor).append("replacementId",replacement.id()).append("beforeArchiveId",replacement.beforeArchiveId())
                .append("beforeSha256",replacement.beforeSha256()).append("createdAt",replacement.createdAt()).append("expiresAt",replacement.expiresAt());
        return new ReviewRemoteStatusObserver.Target(intent,original);
    }
    private static Receipt receipt(String replacementId,ReviewRemoteStatusObserver.Receipt result) {
        return new Receipt(result.id(),replacementId,result.state(),result.observation(),result.receivedAt());
    }
    @Override public void close(){observer.close();}
}
