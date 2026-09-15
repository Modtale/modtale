package net.modtale.service.admin.review;

import net.modtale.model.user.AdminPermission;
import net.modtale.model.user.User;
import net.modtale.service.user.account.AccountService;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.function.BooleanSupplier;

/** Account-bound operations; all work shares the existing repair admission and I/O budget. */
public final class ReviewCancellationAccess {
    public record Check(String id, ReviewOrphanCancellationJournal.Prepared original) {}
    private final AccountService accounts;
    private final ReviewRepairWorkflow workflow;
    private final ReviewOrphanCancellationJournal journal;
    private final ReviewOrphanCancellationExecutor executor;
    private final ReviewCancellationReconciler reconciler;

    public ReviewCancellationAccess(AccountService accounts, ReviewRepairWorkflow workflow,
            ReviewOrphanCancellationJournal journal, ReviewOrphanCancellationExecutor executor,
            ReviewCancellationReconciler reconciler) {
        this.accounts=accounts;this.workflow=workflow;this.journal=journal;this.executor=executor;this.reconciler=reconciler;
    }
    public ReviewOrphanCancellationJournal.Prepared prepare(String isolationId) {
        var authority=authority();uuid(isolationId);
        return workflow.call(allowed->journal.prepare(isolationId,authority.actor(),allowed),authority.allowed());
    }
    public ReviewOrphanCancellationExecutor.Execution execute(ReviewOrphanCancellationJournal.Prepared original) {
        var authority=authority();validate(original);
        return workflow.call(allowed->executor.execute(original,authority.actor(),allowed),authority.allowed());
    }
    public ReviewOrphanCancellationJournal.Receipt receipt(ReviewOrphanCancellationJournal.Prepared original) {
        var authority=authority();validate(original);
        return workflow.call(allowed->journal.receipt(original,authority.actor(),allowed),authority.allowed());
    }
    public ReviewCancellationReconciler.Receipt check(Check request) {
        var authority=authority();validate(request);
        return workflow.call(allowed->reconciler.check(request.id(),request.original(),authority.actor(),allowed),authority.allowed());
    }
    public ReviewCancellationReconciler.Receipt checkReceipt(Check request) {
        var authority=authority();validate(request);
        return workflow.call(allowed->reconciler.receipt(request.id(),request.original(),authority.actor(),allowed),authority.allowed());
    }
    private record Authority(String actor,BooleanSupplier allowed) {}
    private Authority authority() {
        User user=current();if(!allowed(user))throw new SecurityException("Review cancellation is not permitted");
        String actor=user.getId();
        return new Authority(actor,()->{User refreshed=current();return allowed(refreshed)&&actor.equals(refreshed.getId());});
    }
    private User current(){return accounts.getCurrentUser(SecurityContextHolder.getContext().getAuthentication());}
    private static boolean allowed(User user) {
        return user!=null && !user.isDeleted() && user.getId()!=null && !user.getId().isBlank()
                && AdminPermission.hasPermission(user,AdminPermission.PROJECT_REVIEW_READ)
                && AdminPermission.hasPermission(user,AdminPermission.PROJECT_VERSION_RESCAN);
    }
    private static void validate(Check request){if(request==null)throw invalid();uuid(request.id());validate(request.original());}
    private static void validate(ReviewOrphanCancellationJournal.Prepared original) {
        if(original==null)throw invalid();uuid(original.isolationId());
        if(original.targetSha256()==null || !original.targetSha256().matches("[0-9a-f]{64}")
                || original.createdAt()<=0 || original.expiresAt()<=original.createdAt()
                || original.expiresAt()-original.createdAt()>120000)throw invalid();
    }
    private static void uuid(String id){if(id==null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw invalid();}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid review cancellation request");}
}
