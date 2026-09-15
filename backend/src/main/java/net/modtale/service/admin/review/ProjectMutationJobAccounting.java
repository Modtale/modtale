package net.modtale.service.admin.review;

import net.modtale.service.security.scan.RemoteReviewClient;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Observes archived original jobs only; cannot dispatch, cancel, or clear a held mutation. */
public final class ProjectMutationJobAccounting implements AutoCloseable {
    public static final String COLLECTION="project_mutation_job_observations";
    public record Receipt(String id,String mutationId,String versionId,String state,ReviewOrphanCancellationJournal.Observation observation,Long receivedAt) {}
    private final ReviewRepairWorkflow budget;private final ProjectMutationReferenceReader history;
    private final ReviewRemoteTargetReader targets;private final ReviewRemoteStatusObserver observer;
    public ProjectMutationJobAccounting(MongoTemplate mongo,ReviewRepairWorkflow budget,ProjectMutationReferenceReader history,
            RemoteReviewClient client,int concurrency) {
        this.budget=Objects.requireNonNull(budget);this.history=Objects.requireNonNull(history);targets=new ReviewRemoteTargetReader(mongo);
        observer=new ReviewRemoteStatusObserver(mongo,COLLECTION,client,concurrency,Duration.ofSeconds(30));
    }
    public Receipt check(String id,Object projectId,String mutationId,String versionId,BooleanSupplier permitted) {
        return budget.call(allowed->view(mutationId,versionId,observer.check(id,access->target(projectId,mutationId,versionId,access),allowed)),permitted);
    }
    public Receipt receipt(String id,Object projectId,String mutationId,String versionId,BooleanSupplier permitted) {
        return budget.call(allowed->view(mutationId,versionId,observer.receipt(id,access->target(projectId,mutationId,versionId,access),allowed)),permitted);
    }
    private ReviewRemoteStatusObserver.Target target(Object projectId,String mutationId,String versionId,BooleanSupplier permitted) {
        var verified=history.read(projectId,mutationId,permitted);var before=verified.evidence().before();var prepared=verified.evidence().prepared();
        var original=new RawBsonDocument(before.versionBytes()).decode(new DocumentCodec()).getList("versions",Document.class);
        var actual=new RawBsonDocument(verified.applied().versionBytes()).decode(new DocumentCodec()).getList("versions",Document.class);
        var transition=VersionReviewTransition.classify(original,actual).stream().filter(t->Objects.equals(versionId,t.versionId())).findFirst().orElseThrow(ProjectMutationJobAccounting::invalid);
        if(transition.beforeIndex()<0 || transition.changes().stream().noneMatch(change->change!=VersionReviewTransition.Change.METADATA))throw invalid();
        var binding=targets.validate(projectId,original.get(transition.beforeIndex()));
        var intent=new Document("mutationId",mutationId).append("versionId",versionId).append("actor",before.actorId())
                .append("beforeArchiveId",before.id()).append("beforeSha256",prepared.beforeSha256()).append("versionSha256",transition.beforeSha256())
                .append("createdAt",prepared.createdAt()).append("expiresAt",prepared.expiresAt());
        return new ReviewRemoteStatusObserver.Target(intent,binding);
    }
    private static Receipt view(String mutationId,String versionId,ReviewRemoteStatusObserver.Receipt receipt) {
        return new Receipt(receipt.id(),mutationId,versionId,receipt.state(),receipt.observation(),receipt.receivedAt());
    }
    private static IllegalStateException invalid(){return new IllegalStateException("Original mutation job evidence is unavailable");}
    @Override public void close(){observer.close();}
}
