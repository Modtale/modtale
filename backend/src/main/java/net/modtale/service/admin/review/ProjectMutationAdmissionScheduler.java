package net.modtale.service.admin.review;

import net.modtale.service.security.scan.BoundedReviewScheduler;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class ProjectMutationAdmissionScheduler extends BoundedReviewScheduler<ProjectMutationDiscovery.Candidate,ProjectMutationDiscovery.Cursor> {
    private final AtomicLong unavailable;
    public ProjectMutationAdmissionScheduler(ProjectMutationDiscovery discovery,ProjectMutationAutomaticAdmission automatic,Settings settings) {
        this(discovery,automatic,settings,new AtomicLong());
    }
    private ProjectMutationAdmissionScheduler(ProjectMutationDiscovery discovery,ProjectMutationAutomaticAdmission automatic,Settings settings,AtomicLong unavailable) {
        super("mutation-admission",(cursor,limit)->{var page=discovery.page(cursor,limit);unavailable.addAndGet(page.unavailable());return new Page<>(page.candidates(),page.next());},
                (candidate,running)->automatic.advance(candidate,running).state(),settings);
        Objects.requireNonNull(discovery);Objects.requireNonNull(automatic);this.unavailable=unavailable;
    }
    public long unavailableCandidates(){return unavailable.get();}
}
