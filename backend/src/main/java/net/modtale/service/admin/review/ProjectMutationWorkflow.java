package net.modtale.service.admin.review;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Shares repair admission and deadlines; callers must supply fresh project/account authority. */
public final class ProjectMutationWorkflow {
    private final ReviewRepairWorkflow budget;
    private final ProjectMutationPreparation preparation;
    private final ProjectMutationExecutor executor;
    private final ProjectMutationReferenceReader history;
    public ProjectMutationWorkflow(ReviewRepairWorkflow budget,ProjectMutationPreparation preparation,
                                   ProjectMutationExecutor executor,ProjectMutationReferenceReader history) {
        this.budget=Objects.requireNonNull(budget);this.preparation=Objects.requireNonNull(preparation);
        this.executor=Objects.requireNonNull(executor);this.history=Objects.requireNonNull(history);
    }
    public ProjectMutationPreparation.Captured capture(Object projectId,BooleanSupplier permitted) {
        return budget.call(allowed->preparation.capture(projectId,allowed),permitted);
    }
    public ProjectMutationPreparation.Prepared prepare(ProjectMutationPreparation.Request request,BooleanSupplier permitted) {
        return budget.call(allowed->preparation.prepare(request,allowed),permitted);
    }
    public ProjectMutationExecutor.Result apply(ProjectMutationPreparation.Prepared prepared,String actor,BooleanSupplier permitted) {
        return budget.call(allowed->executor.apply(prepared,actor,allowed),permitted);
    }
    public ProjectMutationPreparation.Recovered recover(String id,String actor,BooleanSupplier permitted) {
        return budget.call(allowed->preparation.recover(id,actor,allowed),permitted);
    }
    public ProjectMutationExecutor.Result receipt(ProjectMutationPreparation.Prepared prepared,String actor,BooleanSupplier permitted) {
        return budget.call(allowed->executor.receipt(prepared,actor,allowed),permitted);
    }
    public ProjectMutationReferenceReader.Page page(Object projectId,String cursor,int limit,BooleanSupplier permitted) {
        return budget.call(allowed->history.page(projectId,cursor,limit,allowed),permitted);
    }
    public ProjectMutationReferenceReader.History history(Object projectId,String id,BooleanSupplier permitted) {
        return budget.call(allowed->history.read(projectId,id,allowed),permitted);
    }
}
