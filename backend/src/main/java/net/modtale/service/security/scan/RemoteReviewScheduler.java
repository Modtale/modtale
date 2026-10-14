package net.modtale.service.security.scan;

public final class RemoteReviewScheduler extends BoundedReviewScheduler<RemoteReviewDiscovery.Candidate,RemoteReviewDiscovery.Cursor> {
    public RemoteReviewScheduler(RemoteReviewDiscovery discovery,RemoteReviewBootstrap bootstrap,Settings settings) {
        super("remote-review-poll",(cursor,limit)->{var page=discovery.page(cursor,limit);return new Page<>(page.candidates(),page.next());},
                (candidate,running)->bootstrap.advance(candidate.projectId(),candidate.versionId(),candidate.attempt(),candidate.requestId(),running).state(),settings);
        java.util.Objects.requireNonNull(discovery);java.util.Objects.requireNonNull(bootstrap);
    }
}
