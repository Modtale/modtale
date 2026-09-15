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
        return budget.call(allowed->checkWithinBudget(id,projectId,mutationId,versionId,allowed),permitted);
    }
    Receipt checkWithinBudget(String id,Object projectId,String mutationId,String versionId,BooleanSupplier permitted) {
        return view(mutationId,versionId,observer.check(id,access->target(projectId,mutationId,versionId,access),permitted));
    }
    Receipt checkWithinBudget(String id,Object projectId,String mutationId,String versionId,BooleanSupplier permitted,Duration maximum) {
        return view(mutationId,versionId,observer.check(id,access->target(projectId,mutationId,versionId,access),permitted,maximum));
    }
    public Receipt receipt(String id,Object projectId,String mutationId,String versionId,BooleanSupplier permitted) {
        return budget.call(allowed->receiptWithinBudget(id,projectId,mutationId,versionId,allowed),permitted);
    }
    Receipt receiptWithinBudget(String id,Object projectId,String mutationId,String versionId,BooleanSupplier permitted) {
        return view(mutationId,versionId,observer.receipt(id,access->target(projectId,mutationId,versionId,access),permitted));
    }
    ReadBatch readBatch(Object projectId,BooleanSupplier allowed){return new ReadBatch(projectId,allowed);}
    /** One synchronous read-only pass. No data survives the pass or grants remote execution authority. */
    final class ReadBatch implements AutoCloseable {
        private final Object projectId;private final BooleanSupplier allowed;private final Thread owner=Thread.currentThread();
        private final Map<String,Group> groups=new HashMap<>();private long bytes;private boolean closed;
        ReadBatch(Object projectId,BooleanSupplier allowed){this.projectId=Objects.requireNonNull(projectId);this.allowed=Objects.requireNonNull(allowed);check();}
        Receipt receipt(String id,Object expectedProject,String mutationId,String versionId){
            check();if(!projectId.equals(expectedProject))throw invalid();var group=groups.get(mutationId);
            if(group==null){
                if(groups.size()>=8)throw invalid();var verified=history.read(projectId,mutationId,allowed);
                bytes+=verified.evidence().before().versionBytes().length+verified.evidence().after().versionBytes().length+verified.applied().versionBytes().length;
                if(bytes>64L*1024*1024)throw invalid();group=group(verified);groups.put(mutationId,group);
            }
            var target=target(projectId,mutationId,versionId,group);
            var result=view(mutationId,versionId,observer.receipt(id,permission->{check();return target;},this::permitted));check();return result;
        }
        private boolean permitted(){check();return true;}
        private void check(){if(closed || Thread.currentThread()!=owner)throw invalid();if(!allowed.getAsBoolean())throw new SecurityException("Original job receipt access is not permitted");}
        @Override public void close(){if(Thread.currentThread()!=owner)throw invalid();closed=true;groups.clear();}
    }
    private record Original(Document version,String sha256) {}
    private record Group(String actor,String beforeArchiveId,String beforeSha256,long created,long expires,Map<String,Original> originals) {}
    private Group group(ProjectMutationReferenceReader.History verified){
        var before=verified.evidence().before();var prepared=verified.evidence().prepared();
        var original=new RawBsonDocument(before.versionBytes()).decode(new DocumentCodec()).getList("versions",Document.class);
        var actual=new RawBsonDocument(verified.applied().versionBytes()).decode(new DocumentCodec()).getList("versions",Document.class);
        var changed=new HashMap<String,Original>();
        for(var transition:VersionReviewTransition.classify(original,actual)){
            if(transition.beforeIndex()>=0 && transition.changes().stream().anyMatch(change->change!=VersionReviewTransition.Change.METADATA))
                changed.put(transition.versionId(),new Original(original.get(transition.beforeIndex()),transition.beforeSha256()));
        }
        return new Group(before.actorId(),before.id(),prepared.beforeSha256(),prepared.createdAt(),prepared.expiresAt(),Map.copyOf(changed));
    }
    private ReviewRemoteStatusObserver.Target target(Object projectId,String mutationId,String versionId,Group group){
        var original=group.originals().get(versionId);if(original==null)throw invalid();var binding=targets.validate(projectId,original.version());
        var intent=new Document("mutationId",mutationId).append("versionId",versionId).append("actor",group.actor())
                .append("beforeArchiveId",group.beforeArchiveId()).append("beforeSha256",group.beforeSha256()).append("versionSha256",original.sha256())
                .append("createdAt",group.created()).append("expiresAt",group.expires());
        return new ReviewRemoteStatusObserver.Target(intent,binding);
    }
    private ReviewRemoteStatusObserver.Target target(Object projectId,String mutationId,String versionId,BooleanSupplier permitted) {
        return target(projectId,mutationId,versionId,group(history.read(projectId,mutationId,permitted)));
    }
    private static Receipt view(String mutationId,String versionId,ReviewRemoteStatusObserver.Receipt receipt) {
        return new Receipt(receipt.id(),mutationId,versionId,receipt.state(),receipt.observation(),receipt.receivedAt());
    }
    private static IllegalStateException invalid(){return new IllegalStateException("Original mutation job evidence is unavailable");}
    @Override public void close(){observer.close();}
}
