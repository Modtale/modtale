package net.modtale.service.security.issue;

import java.util.*;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.service.security.scan.ArtifactClearancePolicy;
import net.modtale.service.security.scan.ArtifactReviewContext;
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

    /** A narrow predicate for a fresh independent clean result; callers must still bind the head in their write. */
    public static boolean permitsIndependentClean(MongoTemplate mongo, String projectId, ProjectVersion version,
            ScanResult currentScan, String currentPolicy, long now) {
        if (version == null || version.getFindingReviewHead() == null) return false;
        try {
            return permitsIndependentClean(projectId, version, currentScan,
                    load(mongo, projectId, version.getId(), version.getFindingReviewHead()), currentPolicy, now);
        } catch (RuntimeException unavailableHistory) {
            return false;
        }
    }

    static boolean permitsIndependentClean(String projectId, ProjectVersion version, ScanResult currentScan,
            List<FindingReviewService.Event> events, String currentPolicy, long now) {
        if (version == null || version.getReplacementSecurityHold() != null || version.getFindingReviewHead() == null
                || currentPolicy == null || events == null || events.isEmpty()
                || !version.getFindingReviewHead().equals(events.getFirst().id())
                || !ArtifactClearancePolicy.complete(currentScan) || currentScan.getReusedReviewVersion() != null
                || !"AUTO_APPROVE".equals(currentScan.getVerdict()) || currentScan.getStatus() != ScanStatus.CLEAN
                || !ArtifactClearancePolicy.cleared(currentScan)) return false;
        var evidence = currentScan.getSecurityEvidence();
        String context = ArtifactReviewContext.automaticallyReviewableFingerprint(version);
        if (context == null || !context.equals(currentScan.getReviewedContextSha256())
                || !Objects.equals(version.getHash(), evidence.artifactSha256())
                || !currentPolicy.equals(evidence.policyVersion())) return false;
        var assessments = new FindingDecisionValidity().assess(projectId, version, currentScan, events, currentPolicy, now);
        boolean activeAcceptance = false;
        for (var event : events) {
            if (event.disposition() != FindingReviewService.Disposition.ACCEPT
                    || !Objects.equals(event.artifactSha256(), evidence.artifactSha256())) return false;
            var assessment = assessments.get(event.id());
            if (assessment == null) return false;
            if (assessment.state() == FindingDecisionValidity.State.APPLICABLE) activeAcceptance = true;
            else if (assessment.state() != FindingDecisionValidity.State.SUPERSEDED) return false;
        }
        return activeAcceptance;
    }

    /** This gate removes no findings and grants no automatic clearance. The caller must CAS the head. */
    public static void requireManualApproval(MongoTemplate mongo, String projectId, ProjectVersion version) {
        if(version.getScanResult()!=null && "MUTATION_HELD".equals(version.getScanResult().getScanState()))throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,"The changed version is awaiting retained review admission.");
        if(version.getReplacementSecurityHold()!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Resolve the retained security block before approving this version.");
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
