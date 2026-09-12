package net.modtale.service.security.issue;

import java.util.*;
import net.modtale.config.properties.AppSecurityProperties;
import net.modtale.model.project.*;
import net.modtale.service.security.scan.ScanEvidenceFixtures;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IssueEvidenceIdentityTest {
    private final SecurityIssueEvaluationService evaluation = new SecurityIssueEvaluationService(120);

    @Test void approvalPruningRetainsEvidenceAndNewScanRemainsUnresolved() {
        var prior = approved(scan());
        assertNull(prior.getScanResult());
        assertNotNull(prior.getApprovedIssueBaselines().getFirst().getEvidenceIdentity());
        var current = scan();
        annotate(current, prior);
        assertTrue(current.getIssues().getFirst().isHistoricalFileEvidenceIdentical());
        assertFalse(current.getIssues().getFirst().isResolved());
        assertEquals("REVIEW", current.getVerdict());
    }

    @Test void changedFileBehindSameWarningIsNotIdentical() {
        var prior = approved(scan());
        var current = scan();
        replaceEntries(current, Map.of("Mod.class", "c".repeat(64)));
        annotate(current, prior);
        assertTrue(current.getIssues().getFirst().isKnownIssue());
        assertFalse(current.getIssues().getFirst().isHistoricalFileEvidenceIdentical());
    }

    @Test void changedCallerOrRuntimeCannotTurnSameFileEvidenceIntoClearance() {
        var prior = approved(scan());
        var current = scan();
        replaceEntries(current, Map.of("Mod.class", "a".repeat(64), "Caller.class", "d".repeat(64)));
        current.setReviewedContextSha256("e".repeat(64));
        annotate(current, prior);
        assertTrue(current.getIssues().getFirst().isHistoricalFileEvidenceIdentical());
        assertFalse(current.getIssues().getFirst().isResolved());
        assertFalse(net.modtale.service.security.scan.ArtifactClearancePolicy.cleared(current));
    }

    @Test void exactOccurrencePolicyAndSemanticFieldsAreBound() {
        var original = scan();
        String expected = IssueEvidenceIdentity.from(original).identify(original.getIssues().getFirst());
        for (String change : List.of("line", "end", "type", "description", "severity", "confidence", "cadence", "tactics", "policy")) {
            var changed = scan();
            var issue = changed.getIssues().getFirst();
            switch (change) {
                case "line" -> issue.setLineStart(10); // Same legacy eight-line bucket.
                case "end" -> issue.setLineEnd(12);
                case "type" -> issue.setType("Network|changed");
                case "description" -> issue.setDescription("connect|changed");
                case "severity" -> issue.setSeverity("HIGH");
                case "confidence" -> issue.setConfidence(99);
                case "cadence" -> issue.setReviewCadence("ALWAYS");
                case "tactics" -> issue.setTactics(List.of("exfiltration"));
                case "policy" -> {
                    var e = changed.getSecurityEvidence();
                    changed.setSecurityEvidence(new ScanResult.SecurityEvidence("warden-3.0.0:" + "f".repeat(64),
                            e.artifactSha256(), e.contentSha256(), true, false, e.reviewState(), e.entryHashes()));
                }
            }
            assertNotEquals(expected, IssueEvidenceIdentity.from(changed).identify(issue), change);
        }
    }

    @Test void missingUnverifiedIncompleteCorruptOrUnmappedEvidenceCannotMatch() {
        for (String change : List.of("missing", "unverified", "incomplete", "corrupt", "unmapped")) {
            var scan = scan();
            switch (change) {
                case "missing" -> scan.setSecurityEvidence(null);
                case "unverified" -> scan.setArtifactVerified(false);
                case "incomplete" -> scan.setScanState("FAILED");
                case "corrupt" -> {
                    var e = scan.getSecurityEvidence();
                    scan.setSecurityEvidence(new ScanResult.SecurityEvidence(e.policyVersion(), e.artifactSha256(),
                            "f".repeat(64), true, false, e.reviewState(), e.entryHashes()));
                }
                case "unmapped" -> scan.getIssues().getFirst().setFilePath("Other.class");
            }
            assertNull(IssueEvidenceIdentity.from(scan).identify(scan.getIssues().getFirst()), change);
        }
    }

    @Test void legacyHistoryAndUnboundApprovalCannotInventEvidence() {
        var prior = approved(scan());
        prior.getApprovedIssueBaselines().getFirst().setEvidenceIdentity(null);
        var current = scan();
        current.getIssues().getFirst().setHistoricalFileEvidenceIdentical(true);
        annotate(current, prior);
        assertFalse(current.getIssues().getFirst().isHistoricalFileEvidenceIdentical());
        var version = new ProjectVersion();
        version.setHash("f".repeat(64));
        version.setScanResult(scan());
        approval().markIssuesAcceptedForApprovedVersion(version);
        assertNull(version.getApprovedIssueBaselines().getFirst().getEvidenceIdentity());
    }

    @Test void fieldBoundariesNullsAndMalformedUnicodeCannotAlias() {
        var scan = scan(); var issue = scan.getIssues().getFirst();
        var identities = IssueEvidenceIdentity.from(scan);
        Set<String> hashes = new HashSet<>();
        for (String value : Arrays.asList(null, "", "null", "?", String.valueOf((char) 0xd800),
                String.valueOf((char) 0xd801), "a|b")) {
            issue.setDescription(value);
            assertTrue(hashes.add(identities.identify(issue)));
        }
        issue.setType("a|b"); issue.setCategory("c");
        String before = identities.identify(issue);
        issue.setType("a"); issue.setCategory("b|c");
        assertNotEquals(before, identities.identify(issue));
    }

    @Test void mergedHistoryKeepsIdentityFromTheDisplayedVersion() {
        var a = new SecurityIssueAnalysisService.IssueBaseline("si", "sl", "LOW", 0, 0, "a", "1", 1, 1, "first");
        var b = new SecurityIssueAnalysisService.IssueBaseline("si", "sl", "HIGH", 2, 8, "b", "2", 2, 1, "second");
        assertEquals("second", a.mergeWith(b).evidenceIdentity());
        assertEquals("b", a.mergeWith(b).versionId());
        assertEquals("second", b.mergeWith(a).evidenceIdentity());
    }

    private void annotate(ScanResult current, ProjectVersion prior) {
        var project = new Project(); project.setVersions(List.of(prior));
        evaluation.annotateAgainstBaselines(current,
                new SecurityIssueBaselineService().collectApprovedIssueBaselines(project, null, evaluation));
    }
    private SecurityIssueApprovalService approval() {
        return new SecurityIssueApprovalService(new SecurityIssueClassificationService(new AppSecurityProperties("test", 60, 120, 2, 12, 15, 120, 25, 2)));
    }
    private ProjectVersion approved(ScanResult scan) {
        var version = new ProjectVersion(); version.setId("prior"); version.setVersionNumber("1.0");
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        version.setHash(scan.getSecurityEvidence().artifactSha256()); version.setScanResult(scan);
        approval().markIssuesAcceptedForApprovedVersion(version);
        return version;
    }
    private ScanResult scan() {
        var scan = ScanEvidenceFixtures.complete(false);
        var issue = new ScanResult.ScanIssue(); issue.setType("Network"); issue.setCategory("NETWORK");
        issue.setDescription("connect"); issue.setFilePath("Mod.class"); issue.setSeverity("LOW");
        issue.setLineStart(9); issue.setLineEnd(11);
        scan.setIssues(new ArrayList<>(List.of(issue)));
        return scan;
    }
    private void replaceEntries(ScanResult scan, Map<String,String> entries) {
        var e = scan.getSecurityEvidence();
        scan.setSecurityEvidence(new ScanResult.SecurityEvidence(e.policyVersion(), "c".repeat(64),
                SecurityManifest.identity(entries), true, false, e.reviewState(), entries));
    }
}
