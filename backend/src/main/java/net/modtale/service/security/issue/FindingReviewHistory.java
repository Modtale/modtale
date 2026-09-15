package net.modtale.service.security.issue;

import java.util.*;
import net.modtale.model.project.ProjectVersion;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Reads only the immutable history reachable from the version's authoritative head. */
public final class FindingReviewHistory {
    public static final int MAX_EVENTS = 1000;
    private FindingReviewHistory() {}

    public static List<FindingReviewService.Event> load(MongoTemplate mongo, String projectId, String versionId, String head) {
        var events = new ArrayList<FindingReviewService.Event>();
        var visited = new HashSet<String>();
        Integer expected = null;
        for (String id = head; id != null;) {
            if (!visited.add(id) || events.size() >= MAX_EVENTS) throw unavailable();
            var event = mongo.findById(id, FindingReviewService.Event.class, FindingReviewService.COLLECTION);
            if (event == null || !id.equals(event.id()) || !projectId.equals(event.projectId())
                    || !versionId.equals(event.versionId()) || event.sequence() <= 0 || event.sequence() > MAX_EVENTS
                    || expected != null && event.sequence() != expected) throw unavailable();
            expected = event.sequence() - 1;
            events.add(event); id = event.previousId();
        }
        if (expected != null && expected != 0) throw unavailable();
        var older = new HashMap<String, FindingReviewService.Event>();
        for (int i = events.size() - 1; i >= 0; i--) {
            var event = events.get(i);
            if (event.disposition() == null) throw unavailable();
            if (event.disposition() == FindingReviewService.Disposition.REVOKE) {
                var target = older.get(event.revokedDecisionId());
                if (target == null || target.disposition() == FindingReviewService.Disposition.REVOKE
                        || event.supersedesDecisionId() != null) throw unavailable();
            } else {
                if (event.revokedDecisionId() != null) throw unavailable();
                if (event.supersedesDecisionId() != null) {
                    var target = older.get(event.supersedesDecisionId());
                    if (target == null || target.disposition() == FindingReviewService.Disposition.REVOKE
                            || event.finding() == null || target.finding() == null
                            || !Objects.equals(event.finding().identity(), target.finding().identity())
                            || !Objects.equals(event.contentSha256(), target.contentSha256())
                            || !Objects.equals(event.contextSha256(), target.contextSha256())) throw unavailable();
                }
            }
            older.put(event.id(), event);
        }
        return List.copyOf(events);
    }

    /** This gate removes no findings and grants no automatic clearance. The caller must CAS the head. */
    public static void requireManualApproval(MongoTemplate mongo, String projectId, ProjectVersion version) {
        var events = load(mongo, projectId, version.getId(), version.getFindingReviewHead());
        var assessments = new FindingDecisionValidity().assess(projectId, version, events, null, System.currentTimeMillis());
        if (assessments.values().stream().anyMatch(value -> value.state() == FindingDecisionValidity.State.INVALID_RECORD))
            throw unavailable();
        if (assessments.values().stream().anyMatch(value -> value.state() == FindingDecisionValidity.State.REVIEW_REQUIRED))
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Resolve or explicitly revoke the outstanding finding-review requirements before approving this version.");
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Decision history is incomplete or inconsistent");
    }
}
