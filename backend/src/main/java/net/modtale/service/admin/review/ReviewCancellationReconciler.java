package net.modtale.service.admin.review;

import net.modtale.service.security.scan.RemoteReviewClient;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Separate status observations. No cancellation, replacement or overwrite of the original outcome. */
public final class ReviewCancellationReconciler implements AutoCloseable {
    public static final String COLLECTION="review_cancellation_observations";
    public record Receipt(String id,ReviewOrphanCancellationJournal.Prepared original,String state,
                          ReviewOrphanCancellationJournal.Observation observation,Long receivedAt) {}
    private final ReviewOrphanCancellationJournal journal;
    private final ReviewObservationReader reader;
    private final ReviewRemoteStatusObserver observer;
    public ReviewCancellationReconciler(MongoTemplate mongo,ReviewOrphanCancellationJournal journal,RemoteReviewClient client,int concurrency,Duration budget) {
        this.journal=Objects.requireNonNull(journal);reader=new ReviewObservationReader(mongo);
        observer=new ReviewRemoteStatusObserver(mongo,COLLECTION,client,concurrency,budget);
    }
    public void initializeDiscovery(){reader.initialize();}
    public ReviewObservationReader.Page history(ReviewOrphanCancellationJournal.Prepared original,String actor,String cursor,int limit,BooleanSupplier permitted) {
        permission(permitted);journal.reconciliationTarget(original,actor,permitted);
        var page=reader.page(original,actor,cursor,limit);permission(permitted);return page;
    }
    public Receipt check(String id,ReviewOrphanCancellationJournal.Prepared original,String actor,BooleanSupplier permitted) {
        return receipt(original,observer.check(id,allowed->target(original,actor,allowed),permitted));
    }
    public Receipt receipt(String id,ReviewOrphanCancellationJournal.Prepared original,String actor,BooleanSupplier permitted) {
        return receipt(original,observer.receipt(id,allowed->target(original,actor,allowed),permitted));
    }
    private ReviewRemoteStatusObserver.Target target(ReviewOrphanCancellationJournal.Prepared original,String actor,BooleanSupplier permitted) {
        var target=journal.reconciliationTarget(original,actor,permitted);
        var intent=new Document("actor",actor).append("isolationId",original.isolationId()).append("targetSha256",original.targetSha256())
                .append("createdAt",original.createdAt()).append("expiresAt",original.expiresAt());
        return new ReviewRemoteStatusObserver.Target(intent,target.binding());
    }
    private static Receipt receipt(ReviewOrphanCancellationJournal.Prepared original,ReviewRemoteStatusObserver.Receipt result) {
        return new Receipt(result.id(),original,result.state(),result.observation(),result.receivedAt());
    }
    private static void permission(BooleanSupplier permitted){if(permitted==null || !permitted.getAsBoolean())throw new SecurityException("Reconciliation is not permitted");}
    @Override public void close(){observer.close();}
}
