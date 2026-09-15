package net.modtale.service.security.issue;

import java.util.*;
import net.modtale.model.project.*;
import net.modtale.service.security.scan.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FindingDecisionValidityTest {
    private static final long NOW = 1_800_000_000_000L;
    private final FindingDecisionValidity evaluator = new FindingDecisionValidity();
    private ProjectVersion version() {
        var version = new ProjectVersion(); version.setId("v");
        var scan = ScanEvidenceFixtures.complete(false); version.setScanResult(scan);
        version.setHash(scan.getSecurityEvidence().artifactSha256());
        scan.setReviewedContextSha256(ArtifactReviewContext.fingerprint(version));
        var issue = new ScanResult.ScanIssue(); issue.setFilePath("Mod.class"); issue.setType("Network");
        issue.setDescription("Connects to a service"); issue.setSeverity("LOW"); issue.setLineStart(9); issue.setLineEnd(11);
        scan.setIssues(new ArrayList<>(List.of(issue))); return version;
    }
    private FindingReviewService.Event event(ProjectVersion version, String id, FindingReviewService.Disposition disposition,
            String revoked, String supersedes, long created, long expires) {
        var scan = version.getScanResult(); var e = scan.getSecurityEvidence(); var issue = scan.getIssues().getFirst();
        return new FindingReviewService.Event(id, "p", "v", null, 1, "reviewer", created, expires, disposition,
                "Inspected the complete integration", "WHOLE_ARTIFACT", e.artifactSha256(), e.contentSha256(), e.policyVersion(),
                ArtifactReviewContext.fingerprint(version), new FindingReviewService.Finding(IssueEvidenceIdentity.from(scan).identify(issue),
                        issue.getFilePath(), issue.getType(), issue.getDescription(), issue.getLineStart(), issue.getLineEnd()), revoked, supersedes);
    }
    private FindingReviewService.Event acceptance(ProjectVersion version) {
        return event(version, "accepted", FindingReviewService.Disposition.ACCEPT, null, null, NOW - 1000, NOW + 1000);
    }
    private FindingDecisionValidity.State state(ProjectVersion version, FindingReviewService.Event event, String policy) {
        return evaluator.assess("p", version, List.of(event), policy, NOW).get(event.id()).state();
    }
    @Test void matchingReasoningDoesNotMutateClearanceOrResolveTheFinding() {
        var version = version(); var event = acceptance(version);
        assertEquals(FindingDecisionValidity.State.APPLICABLE, state(version, event, event.policyVersion()));
        assertFalse(version.getScanResult().getIssues().getFirst().isResolved());
        assertFalse(ArtifactClearancePolicy.cleared(version.getScanResult()));
    }
    @Test void artifactCallerResourceAndDependencyChangesInvalidateWholeArtifactScope() {
        for (String change : List.of("caller", "resource", "dependency", "runtime", "supplement", "unbound")) {
            var version = version(); var event = acceptance(version); var scan = version.getScanResult();
            FindingDecisionValidity.State expected;
            if (change.equals("caller") || change.equals("resource")) {
                var e = scan.getSecurityEvidence();
                var entries = new HashMap<>(e.entryHashes()); entries.put(change + ".data", "d".repeat(64));
                scan.setSecurityEvidence(new ScanResult.SecurityEvidence(e.policyVersion(), e.artifactSha256(), SecurityManifest.identity(entries),
                        true, false, e.reviewState(), entries));
                expected = FindingDecisionValidity.State.ARTIFACT_CHANGED;
            } else if (change.equals("unbound")) {
                version.setHash("d".repeat(64)); expected = FindingDecisionValidity.State.ARTIFACT_CHANGED;
            } else {
                if (change.equals("runtime")) version.setGameVersions(List.of("new-runtime"));
                else if (change.equals("dependency")) version.setDependencies(List.of(new ProjectDependency("dependency", "Dependency", "1.0")));
                else version.setOverrideFileUrl("supplement.zip");
                expected = FindingDecisionValidity.State.CONTEXT_CHANGED;
            }
            assertEquals(expected, state(version, event, event.policyVersion()), change);
        }
    }
    @Test void sameVerifiedContentIdentityAllowsPackagingChange() {
        var version = version(); var event = acceptance(version); var scan = version.getScanResult(); var e = scan.getSecurityEvidence();
        version.setHash("d".repeat(64));
        scan.setSecurityEvidence(new ScanResult.SecurityEvidence(e.policyVersion(), version.getHash(), e.contentSha256(), true, false, e.reviewState(), e.entryHashes()));
        assertEquals(FindingDecisionValidity.State.APPLICABLE, state(version, event, event.policyVersion()));
    }
    @Test void newerPolicyUnavailablePolicyAndChangedOccurrenceCannotRetainAcceptance() {
        var version = version(); var event = acceptance(version);
        assertEquals(FindingDecisionValidity.State.POLICY_UNAVAILABLE, state(version, event, null));
        assertEquals(FindingDecisionValidity.State.POLICY_CHANGED, state(version, event, "warden-3.0.0:" + "f".repeat(64)));
        version.getScanResult().getIssues().getFirst().setLineStart(10);
        assertEquals(FindingDecisionValidity.State.FINDING_CHANGED, state(version, event, event.policyVersion()));
    }
    @Test void incompleteOrAdverseEvidenceCannotBorrowAnAcceptance() {
        for (String change : List.of("unverified", "incomplete", "missing-manifest", "block", "new-evidence")) {
            var version = version(); var event = acceptance(version); var scan = version.getScanResult(); var e = scan.getSecurityEvidence();
            switch (change) {
                case "unverified" -> scan.setArtifactVerified(false);
                case "incomplete" -> scan.setScanState("INCOMPLETE");
                case "missing-manifest" -> scan.setSecurityEvidence(new ScanResult.SecurityEvidence(e.policyVersion(), e.artifactSha256(), e.contentSha256(), true, false, e.reviewState(), Map.of()));
                case "block" -> scan.setVerdict("BLOCK");
                case "new-evidence" -> scan.setSecurityEvidence(new ScanResult.SecurityEvidence(e.policyVersion(), e.artifactSha256(), e.contentSha256(), true, false, "NEW_SECURITY_EVIDENCE", e.entryHashes()));
            }
            assertEquals(change.equals("block") || change.equals("new-evidence") ? FindingDecisionValidity.State.ADVERSE_EVIDENCE
                    : FindingDecisionValidity.State.INCOMPLETE_EVIDENCE, state(version, event, event.policyVersion()), change);
        }
    }
    @Test void expiryFutureDatesAndCrossVersionProvenanceAreChecked() {
        var version = version(); var event = acceptance(version);
        assertEquals(FindingDecisionValidity.State.EXPIRED, evaluator.assess("p", version, List.of(event), event.policyVersion(), NOW + 1000).get(event.id()).state());
        assertEquals(FindingDecisionValidity.State.INVALID_RECORD, evaluator.assess("p", version, List.of(event), event.policyVersion(), NOW - 1001).get(event.id()).state());
        assertEquals(FindingDecisionValidity.State.INVALID_RECORD, evaluator.assess("different-project", version, List.of(event), event.policyVersion(), NOW).get(event.id()).state());
        version.setId("different-version");
        assertEquals(FindingDecisionValidity.State.INVALID_RECORD, state(version, event, event.policyVersion()));
    }
    @Test void revokedReplacementDoesNotResurrectSupersededAcceptance() {
        var version = version(); var first = acceptance(version);
        var second = event(version, "replacement", FindingReviewService.Disposition.REQUIRE_REVIEW, null, first.id(), NOW - 500, 0);
        var revoke = event(version, "revocation", FindingReviewService.Disposition.REVOKE, second.id(), null, NOW, 0);
        var result = evaluator.assess("p", version, List.of(revoke, second, first), first.policyVersion(), NOW);
        assertEquals(FindingDecisionValidity.State.SUPERSEDED, result.get(first.id()).state());
        assertEquals(FindingDecisionValidity.State.REVOKED, result.get(second.id()).state());
        assertEquals(FindingDecisionValidity.State.REVOCATION, result.get(revoke.id()).state());
    }
    @Test void extendedExpiryUnknownScopeAndMisrepresentedFindingCannotApply() {
        for (String change : List.of("expiry", "scope", "description")) {
            var version = version(); var original = acceptance(version);
            var finding = original.finding();
            if (change.equals("description")) finding = new FindingReviewService.Finding(finding.identity(), finding.path(),
                    finding.type(), "Different displayed evidence", finding.lineStart(), finding.lineEnd());
            var altered = new FindingReviewService.Event(original.id(), original.projectId(), original.versionId(), original.previousId(),
                    original.sequence(), original.actorId(), original.createdAt(), change.equals("expiry") ? NOW + 31L * 86400000 : original.expiresAt(),
                    original.disposition(), original.rationale(), change.equals("scope") ? "ANY_PROJECT" : original.scope(), original.artifactSha256(),
                    original.contentSha256(), original.policyVersion(), original.contextSha256(), finding, null, null);
            var expected = switch (change) {
                case "expiry" -> FindingDecisionValidity.State.INVALID_RECORD;
                case "scope" -> FindingDecisionValidity.State.UNSUPPORTED_SCOPE;
                default -> FindingDecisionValidity.State.FINDING_CHANGED;
            };
            assertEquals(expected, state(version, altered, altered.policyVersion()), change);
        }
    }
    @Test void reviewRequirementsNeverExpireIntoAcceptance() {
        var version = version(); var hold = event(version, "hold", FindingReviewService.Disposition.REQUIRE_REVIEW, null, null, NOW - 1000, 0);
        assertEquals(FindingDecisionValidity.State.REVIEW_REQUIRED,
                evaluator.assess("p", version, List.of(hold), hold.policyVersion(), NOW + 100L * 86400000).get(hold.id()).state());
    }
}
