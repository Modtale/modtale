package net.modtale.service.admin.review;

import net.modtale.model.project.RemoteReviewBinding;
import org.bson.*;
import org.bson.codecs.DocumentCodec;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Authenticated prior-work inventory; absence of a usable job identity is never proof of no work. */
public final class ProjectMutationPriorWorkReader {
    public enum Kind { NEW_VERSION, REMOTE_JOB, UNRESOLVED }
    public record Work(String mutationId,String versionId,String beforeSha256,Kind kind,RemoteReviewBinding binding,String reason) {}
    public record Inventory(String mutationId,List<String> groups,List<Work> work) {
        public Inventory {groups=List.copyOf(groups);work=List.copyOf(work);}
    }
    private static final int MAX_GROUPS=8,MAX_WORK=256;
    private static final long MAX_BYTES=64L*1024*1024;
    private final ReviewRepairWorkflow budget;
    private final ProjectMutationReferenceReader history;
    private final ReviewRemoteTargetReader targets;
    public ProjectMutationPriorWorkReader(MongoTemplate mongo,ReviewRepairWorkflow budget,ProjectMutationReferenceReader history) {
        this.budget=Objects.requireNonNull(budget);this.history=Objects.requireNonNull(history);targets=new ReviewRemoteTargetReader(mongo);
    }
    public Inventory read(Object projectId,String mutationId,BooleanSupplier permitted) {
        return budget.call(allowed->readWithinBudget(projectId,mutationId,allowed),permitted);
    }
    Inventory readWithinBudget(Object projectId,String mutationId,BooleanSupplier permitted) {
        var walk=new Walk(projectId,permitted);walk.visit(mutationId);return new Inventory(mutationId,new ArrayList<>(walk.groups.keySet()),walk.work);
    }
    private final class Walk {
        final Object projectId;final BooleanSupplier allowed;
        final Map<String,ProjectMutationReferenceReader.History> groups=new LinkedHashMap<>();
        final Set<String> active=new HashSet<>(),finished=new HashSet<>();final List<Work> work=new ArrayList<>();long retainedBytes;
        Walk(Object projectId,BooleanSupplier allowed){this.projectId=projectId;this.allowed=allowed;}
        ProjectMutationReferenceReader.History load(String id) {
            permission();var found=groups.get(id);if(found!=null)return found;
            if(groups.size()>=MAX_GROUPS)throw unavailable();
            found=history.read(projectId,id,allowed);
            retainedBytes+=found.evidence().before().versionBytes().length;
            retainedBytes+=found.evidence().after().versionBytes().length;
            retainedBytes+=found.applied().versionBytes().length;
            if(retainedBytes>MAX_BYTES)throw unavailable();groups.put(id,found);return found;
        }
        void visit(String id) {
            permission();if(active.contains(id))throw unavailable();if(finished.contains(id))return;
            var group=load(id);active.add(id);
            var before=versions(group.evidence().before());var after=versions(group.applied());
            for(var change:VersionReviewTransition.classify(before,after)) {
                permission();
                if(change.changes().stream().noneMatch(c->c!=VersionReviewTransition.Change.METADATA))continue;
                if(change.beforeIndex()<0) {add(new Work(id,change.versionId(),null,Kind.NEW_VERSION,null,"NO_PREVIOUS_VERSION"));continue;}
                var original=before.get(change.beforeIndex());Object pointer=original.get("versionMutation");
                var scan=original.get("scanResult") instanceof Document doc?doc:null;
                if(pointer!=null && scan!=null && "MUTATION_HELD".equals(scan.get("scanState"))) {
                    if(!(pointer instanceof Document ref) || ref.size()!=3 || !(ref.get("operationId") instanceof String previous))throw unavailable();
                    var prior=load(previous);var matching=versions(prior.applied()).stream().filter(v->change.versionId().equals(v.get("_id"))).toList();
                    if(matching.size()!=1 || VersionReviewTransition.classify(matching,List.of(original)).getFirst().changes().stream()
                            .anyMatch(c->c!=VersionReviewTransition.Change.METADATA))throw unavailable();
                    visit(previous);continue;
                }
                if(scan!=null && "MUTATION_HELD".equals(scan.get("scanState")))throw unavailable();
                RemoteReviewBinding binding=null;
                try{binding=targets.validate(projectId,original);}catch(IllegalStateException missing){ /* Explicit uncertainty below. */ }
                add(new Work(id,change.versionId(),change.beforeSha256(),binding==null?Kind.UNRESOLVED:Kind.REMOTE_JOB,binding,
                        binding==null?"ORIGINAL_JOB_IDENTITY_UNAVAILABLE":null));
                if(pointer!=null || original.get("reviewReplacement")!=null || original.get("reviewIsolation")!=null)
                    add(new Work(id,change.versionId(),change.beforeSha256(),Kind.UNRESOLVED,null,"PRIOR_TRANSITION_REQUIRES_ACCOUNTING"));
            }
            active.remove(id);finished.add(id);permission();
        }
        void add(Work item){if(work.size()>=MAX_WORK)throw unavailable();work.add(item);}
        void permission(){if(!allowed.getAsBoolean())throw new SecurityException("Prior work access is not permitted");}
    }
    private static List<Document> versions(ReviewSnapshotArchive.Snapshot snapshot) {
        return new RawBsonDocument(snapshot.versionBytes()).decode(new DocumentCodec()).getList("versions",Document.class);
    }
    private static IllegalStateException unavailable(){return new IllegalStateException("Prior mutation work is unavailable or exceeds the inspection budget");}
}
