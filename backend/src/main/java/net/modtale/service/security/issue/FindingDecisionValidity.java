package net.modtale.service.security.issue;

import java.util.*;
import net.modtale.model.project.*;
import net.modtale.service.security.scan.ArtifactClearancePolicy;
import net.modtale.service.security.scan.ArtifactReviewContext;

/** Evaluates recorded reasoning; no assessment here grants whole-artifact clearance. */
final class FindingDecisionValidity {
    enum State {
        APPLICABLE, REVIEW_REQUIRED, REVOKED, SUPERSEDED, EXPIRED, INVALID_RECORD,
        INCOMPLETE_EVIDENCE, ARTIFACT_CHANGED, CONTEXT_CHANGED, POLICY_CHANGED,
        POLICY_UNAVAILABLE, FINDING_CHANGED, ADVERSE_EVIDENCE, UNSUPPORTED_SCOPE, REVOCATION
    }
    record Assessment(State state, String explanation) {}

    Map<String, Assessment> assess(String projectId, ProjectVersion version, List<FindingReviewService.Event> events,
            String currentPolicy, long now) {
        var results = new LinkedHashMap<String, Assessment>();
        var revoked = new HashSet<String>();
        var superseded = new HashSet<String>();
        var scan = version == null ? null : version.getScanResult();
        var evidence = scan == null ? null : scan.getSecurityEvidence();
        boolean complete = ArtifactClearancePolicy.complete(scan);
        var identities = IssueEvidenceIdentity.from(scan);
        var occurrences = new HashMap<String, ScanResult.ScanIssue>();
        if (scan != null && scan.getIssues() != null) for (var issue : scan.getIssues()) {
            String identity = identities.identify(issue);
            if (identity != null) occurrences.put(identity, issue);
        }
        String context = ArtifactReviewContext.fingerprint(version);
        // The caller supplies the complete, linked newest-first chain. Revoked superseding decisions
        // do not resurrect earlier conclusions: a fresh explicit decision is required.
        for (var event : events) {
            if (event.revokedDecisionId() != null) revoked.add(event.revokedDecisionId());
            if (event.supersedesDecisionId() != null) superseded.add(event.supersedesDecisionId());
        }
        for (var event : events) {
            State state;
            if (!validRecord(event, projectId, version, now)) state = State.INVALID_RECORD;
            else if (event.disposition() == FindingReviewService.Disposition.REVOKE) state = State.REVOCATION;
            else if (revoked.contains(event.id())) state = State.REVOKED;
            else if (superseded.contains(event.id())) state = State.SUPERSEDED;
            else if (event.disposition() == FindingReviewService.Disposition.REQUIRE_REVIEW) state = State.REVIEW_REQUIRED;
            else if (event.expiresAt() <= now) state = State.EXPIRED;
            else if (!"WHOLE_ARTIFACT".equals(event.scope())) state = State.UNSUPPORTED_SCOPE;
            else if (!complete) state = State.INCOMPLETE_EVIDENCE;
            else if ("BLOCK".equals(scan.getVerdict()) || scan.getStatus() == ScanStatus.INFECTED
                    || "NEW_SECURITY_EVIDENCE".equals(evidence.reviewState())) state = State.ADVERSE_EVIDENCE;
            else if (!Objects.equals(version.getHash(), evidence.artifactSha256())
                    || !event.contentSha256().equals(evidence.contentSha256())) state = State.ARTIFACT_CHANGED;
            else if (context == null || !context.equals(scan.getReviewedContextSha256())
                    || !context.equals(event.contextSha256())) state = State.CONTEXT_CHANGED;
            else if (currentPolicy == null) state = State.POLICY_UNAVAILABLE;
            else if (!currentPolicy.equals(evidence.policyVersion()) || !currentPolicy.equals(event.policyVersion())) state = State.POLICY_CHANGED;
            else if (!sameFinding(event.finding(), occurrences.get(event.finding().identity()))) state = State.FINDING_CHANGED;
            else state = State.APPLICABLE;
            results.put(event.id(), new Assessment(state, explanation(state)));
        }
        return Collections.unmodifiableMap(results);
    }
    private boolean sameFinding(FindingReviewService.Finding recorded, ScanResult.ScanIssue current) {
        return current != null && Objects.equals(recorded.path(), current.getFilePath())
                && Objects.equals(recorded.type(), current.getType()) && Objects.equals(recorded.description(), current.getDescription())
                && recorded.lineStart() == current.getLineStart() && recorded.lineEnd() == current.getLineEnd();
    }
    boolean validRecord(FindingReviewService.Event event, String projectId, ProjectVersion version, long now) {
        if (version == null || !Objects.equals(projectId, event.projectId()) || !Objects.equals(version.getId(), event.versionId())
                || event.disposition() == null || event.actorId() == null || event.actorId().isBlank() || event.actorId().length() > 256
                || event.rationale() == null || event.rationale().strip().length() < 10 || event.rationale().length() > 4000 || event.createdAt() <= 0
                || event.createdAt() > now || !SecurityManifest.digest(event.artifactSha256())
                || !SecurityManifest.digest(event.contentSha256()) || !SecurityManifest.digest(event.contextSha256())
                || event.policyVersion() == null || !event.policyVersion().matches("warden-3\\.0\\.0:[0-9a-f]{64}")
                || event.finding() == null || event.finding().identity() == null
                || !event.finding().identity().matches("ie1:[0-9a-f]{64}")) return false;
        return event.disposition() != FindingReviewService.Disposition.ACCEPT
                || event.expiresAt() > event.createdAt() && event.expiresAt() - event.createdAt() <= 30L * 86400000;
    }
    private String explanation(State state) {
        return switch (state) {
            case APPLICABLE -> "The recorded finding, complete artifact contents, context and current policy still match. This acceptance covers the finding, not publication.";
            case REVIEW_REQUIRED -> "A reviewer requested further investigation. This requirement does not expire automatically.";
            case REVOKED -> "A later decision revoked this conclusion. It cannot be reused.";
            case SUPERSEDED -> "A later conclusion replaced this decision. Revoking its replacement does not restore it.";
            case EXPIRED -> "The acceptance has expired; inspect the current evidence again.";
            case INVALID_RECORD -> "The decision lacks valid provenance or time bounds.";
            case INCOMPLETE_EVIDENCE -> "Current complete, verified evidence is unavailable. Earlier reasoning cannot fill this gap.";
            case ARTIFACT_CHANGED -> "The artifact identity or contents changed. This decision covers the whole artifact, including callers and resources.";
            case CONTEXT_CHANGED -> "The runtime, dependencies, supplemental content or reviewed context no longer matches.";
            case POLICY_CHANGED -> "The scanner policy changed; the earlier conclusion needs evaluation under the current policy.";
            case POLICY_UNAVAILABLE -> "The current scanner policy could not be verified.";
            case FINDING_CHANGED -> "The exact finding is absent or changed in the current scan.";
            case ADVERSE_EVIDENCE -> "The current scan contains adverse evidence that prevents retaining this acceptance.";
            case UNSUPPORTED_SCOPE -> "This decision scope is not supported by the current evaluator.";
            case REVOCATION -> "This event records a revocation and grants no acceptance.";
        };
    }
}
