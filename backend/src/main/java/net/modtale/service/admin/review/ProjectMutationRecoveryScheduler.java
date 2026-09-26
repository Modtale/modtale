package net.modtale.service.admin.review;

import net.modtale.service.security.scan.BoundedReviewScheduler;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class ProjectMutationRecoveryScheduler extends BoundedReviewScheduler<ProjectMutationAdmissionAttempts.Scope,String> {
    private final AtomicLong unavailable;
    public ProjectMutationRecoveryScheduler(ProjectMutationAttemptRecovery recovery,Settings settings){this(recovery,settings,new AtomicLong());}
    private ProjectMutationRecoveryScheduler(ProjectMutationAttemptRecovery recovery,Settings settings,AtomicLong unavailable) {
        super("mutation-admission-recovery",(cursor,limit)->{var page=recovery.page(cursor,limit);unavailable.addAndGet(page.unavailable());return new Page<>(page.candidates(),page.next());},recovery::recover,settings);
        Objects.requireNonNull(recovery);this.unavailable=unavailable;
    }
    public long unavailableCandidates(){return unavailable.get();}
}
