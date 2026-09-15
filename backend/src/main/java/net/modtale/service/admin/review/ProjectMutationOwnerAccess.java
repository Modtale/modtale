package net.modtale.service.admin.review;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Internal server-proposal entry point. Upload/context validation belongs to the owning command handler. */
public final class ProjectMutationOwnerAccess {
    private final ReviewRepairWorkflow budget;private final ProjectMutationOwnerAuthority authority;
    private final ProjectMutationPreparation preparation;private final ProjectMutationExecutor executor;private final ProjectMutationReferenceReader history;
    public ProjectMutationOwnerAccess(ReviewRepairWorkflow budget,ProjectMutationOwnerAuthority authority,ProjectMutationPreparation preparation,
                                      ProjectMutationExecutor executor,ProjectMutationReferenceReader history) {
        this.budget=Objects.requireNonNull(budget);this.authority=Objects.requireNonNull(authority);this.preparation=Objects.requireNonNull(preparation);
        this.executor=Objects.requireNonNull(executor);this.history=Objects.requireNonNull(history);
    }
    public ProjectMutationPreparation.Prepared prepare(String id,Object projectId,String expectedSha256,ProjectMutationPreparation.Mutation mutation,byte[] proposed) {
        if(proposed==null || proposed.length<5 || proposed.length>ReviewSnapshotArchive.MAX_BYTES)throw invalid();
        byte[] proposal=proposed.clone();
        return budget.call(allowed->{
            String actor=authority.actor();var current=preparation.capture(projectId,allowed);
            if(!current.sha256().equals(expectedSha256))throw invalid();
            var required=ProjectMutationPreparation.requiredPermissions(current.bytes(),proposal,projectId,mutation);
            var bound=authority.bind(projectId,required);if(!actor.equals(bound.actor()))throw invalid();
            BooleanSupplier permitted=()->allowed.getAsBoolean() && bound.permitted().getAsBoolean();
            history.requireHeldHeads(projectId,current.bytes(),permitted);
            return preparation.prepare(new ProjectMutationPreparation.Request(id,projectId,expectedSha256,actor,mutation,proposal),permitted);
        },()->true);
    }
    public ProjectMutationExecutor.Result apply(ProjectMutationPreparation.Prepared prepared) {
        Objects.requireNonNull(prepared);
        return budget.call(allowed->{
            String actor=authority.actor();var recovered=preparation.recover(prepared.id(),actor,allowed);if(!prepared.equals(recovered.prepared()))throw invalid();
            var before=recovered.before();var required=ProjectMutationPreparation.requiredPermissions(before.versionBytes(),recovered.after().versionBytes(),before.projectId(),prepared.mutation());
            var bound=authority.bind(before.projectId(),required);if(!actor.equals(bound.actor()))throw invalid();
            BooleanSupplier permitted=()->allowed.getAsBoolean() && bound.permitted().getAsBoolean();
            history.requireHeldHeads(before.projectId(),before.versionBytes(),permitted);
            return executor.apply(prepared,actor,permitted);
        },()->true);
    }
    private static IllegalStateException invalid(){return new IllegalStateException("Owner mutation state changed");}
}
