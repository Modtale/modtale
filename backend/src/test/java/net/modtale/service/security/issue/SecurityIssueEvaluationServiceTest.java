package net.modtale.service.security.issue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityIssueEvaluationServiceTest {

    private SecurityIssueEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new SecurityIssueEvaluationService(30);
    }

    @Test
    void fingerprintNormalizesCasePathsLineBucketsAndVolatileDescriptionValues() {
        ScanResult.ScanIssue first = issue(
                "Dangerous Call",
                "Runtime/Reflection",
                "Connects to 192.168.0.55 with token 12345",
                "outer.jar-> COM/Example/Deep/Path/Foo.class",
                42,
                "HIGH",
                10,
                70
        );
        ScanResult.ScanIssue second = issue(
                "dangerous-call",
                "runtime reflection",
                "Connects to 10.0.0.1 with token 999",
                "com/example/deep/path/foo.class",
                47,
                "HIGH",
                10,
                70
        );

        assertEquals(service.fingerprint(first), service.fingerprint(second));
        assertEquals(service.looseFingerprint(first), service.looseFingerprint(second));
        assertTrue(service.fingerprint(first).startsWith("si:"));
        assertTrue(service.looseFingerprint(first).startsWith("sl:"));
    }

    @Test
    void normalizeScanResultInitializesMissingFieldsAndDerivesStatusFromVerdict() {
        ScanResult scanResult = new ScanResult();
        scanResult.setVerdict("BLOCK");
        scanResult.setIssues(null);
        scanResult.setReviewerNotes(null);
        scanResult.setReviewTargets(null);

        service.normalizeScanResult(scanResult);

        assertEquals(ScanStatus.INFECTED, scanResult.getStatus());
        assertTrue(scanResult.getIssues().isEmpty());
        assertTrue(scanResult.getReviewerNotes().isEmpty());
        assertTrue(scanResult.getReviewTargets().isEmpty());
        assertTrue(scanResult.getScanTimestamp() > 0);
        assertEquals(1, scanResult.getScanAttempt());
    }

    @Test
    void annotateAgainstBaselinesMarksFreshIssuesWhenNoBaselineExists() {
        ScanResult.ScanIssue freshIssue = issue("New", "Network", "New callback", "Foo.class", 1, "MEDIUM", 5, 50);
        ScanResult scanResult = scan(freshIssue);

        SecurityIssueAnalysisService.ClassificationStats stats =
                service.annotateAgainstBaselines(scanResult, emptyBaselines());

        assertEquals(0, stats.knownIssueCount());
        assertEquals(1, stats.newIssueCount());
        assertEquals(0, stats.escalatedIssueCount());
        assertFalse(stats.knownOnly());
        assertFalse(freshIssue.isKnownIssue());
        assertFalse(freshIssue.isEscalated());
        assertNotNull(freshIssue.getFingerprint());
    }

    @Test
    void annotateAgainstBaselinesResolvesKnownExactMatchesWithoutEscalation() {
        ScanResult.ScanIssue knownIssue = issue("Known", "Runtime", "Known thing", "Foo.class", 1, "MEDIUM", 5, 50);
        String fingerprint = service.fingerprint(knownIssue);
        String looseFingerprint = service.looseFingerprint(knownIssue);
        ScanResult scanResult = scan(knownIssue);

        SecurityIssueAnalysisService.ClassificationStats stats = service.annotateAgainstBaselines(
                scanResult,
                new SecurityIssueAnalysisService.BaselineIndex(
                        Map.of(fingerprint, baseline(fingerprint, looseFingerprint, "MEDIUM", 5, 50, 2)),
                        Map.of()
                )
        );

        assertEquals(1, stats.knownIssueCount());
        assertEquals(0, stats.newIssueCount());
        assertEquals(0, stats.escalatedIssueCount());
        assertTrue(stats.knownOnly());
        assertTrue(knownIssue.isKnownIssue());
        assertTrue(knownIssue.isResolved());
        assertFalse(knownIssue.isEscalated());
        assertEquals("1.0.0", knownIssue.getBaselineVersion());
        assertEquals(5, knownIssue.getBaselineScoreImpact());
        assertEquals("MEDIUM", knownIssue.getBaselineSeverity());
    }

    @Test
    void annotateAgainstBaselinesEscalatesSeverityLooseMatchesWeakHistoryStaleAndAlwaysReviewIssues() {
        ScanResult.ScanIssue severityEscalation = issue("A", "Runtime", "One", "A.class", 1, "HIGH", 5, 50);
        ScanResult.ScanIssue looseMatch = issue("B", "Runtime", "Two", "different/B.class", 1, "LOW", 2, 20);
        ScanResult.ScanIssue weakHistory = issue("C", "Runtime", "Three", "C.class", 1, "HIGH", 8, 20);
        ScanResult.ScanIssue staleBaseline = issue("D", "Runtime", "Four", "D.class", 1, "LOW", 2, 20);
        ScanResult.ScanIssue alwaysReview = issue("E", "Runtime", "Five", "E.class", 1, "LOW", 2, 20);
        alwaysReview.setReviewCadence("ALWAYS");

        Map<String, SecurityIssueAnalysisService.IssueBaseline> exact = new HashMap<>();
        exact.put(service.fingerprint(severityEscalation), baseline(service.fingerprint(severityEscalation), service.looseFingerprint(severityEscalation), "LOW", 5, 50, 2));
        exact.put(service.fingerprint(weakHistory), baseline(service.fingerprint(weakHistory), service.looseFingerprint(weakHistory), "HIGH", 8, 20, 1));
        exact.put(service.fingerprint(staleBaseline), new SecurityIssueAnalysisService.IssueBaseline(
                service.fingerprint(staleBaseline),
                service.looseFingerprint(staleBaseline),
                "LOW",
                2,
                20,
                "version-1",
                "1.0.0",
                Instant.now().minusSeconds(60L * 60L * 24L * 60L).toEpochMilli(),
                2
        ));
        exact.put(service.fingerprint(alwaysReview), baseline(service.fingerprint(alwaysReview), service.looseFingerprint(alwaysReview), "LOW", 2, 20, 2));

        Map<String, SecurityIssueAnalysisService.IssueBaseline> loose = Map.of(
                service.looseFingerprint(looseMatch),
                baseline("different-exact", service.looseFingerprint(looseMatch), "LOW", 2, 20, 2)
        );

        SecurityIssueAnalysisService.ClassificationStats stats = service.annotateAgainstBaselines(
                scan(severityEscalation, looseMatch, weakHistory, staleBaseline, alwaysReview),
                new SecurityIssueAnalysisService.BaselineIndex(exact, loose)
        );

        assertEquals(5, stats.knownIssueCount());
        assertEquals(0, stats.newIssueCount());
        assertEquals(5, stats.escalatedIssueCount());
        assertTrue(List.of(severityEscalation, looseMatch, weakHistory, staleBaseline, alwaysReview)
                .stream()
                .allMatch(ScanResult.ScanIssue::isEscalated));
    }

    @Test
    void normalizeSeverityDefaultsUnknownValuesToLow() {
        assertEquals("LOW", service.normalizeSeverity(null));
        assertEquals("LOW", service.normalizeSeverity("informational"));
        assertEquals("CRITICAL", service.normalizeSeverity("critical"));
    }

    private static SecurityIssueAnalysisService.BaselineIndex emptyBaselines() {
        return new SecurityIssueAnalysisService.BaselineIndex(Map.of(), Map.of());
    }

    private static SecurityIssueAnalysisService.IssueBaseline baseline(
            String fingerprint,
            String looseFingerprint,
            String severity,
            int scoreImpact,
            int confidence,
            int seenCount
    ) {
        return new SecurityIssueAnalysisService.IssueBaseline(
                fingerprint,
                looseFingerprint,
                severity,
                scoreImpact,
                confidence,
                "version-1",
                "1.0.0",
                Instant.now().toEpochMilli(),
                seenCount
        );
    }

    private static ScanResult scan(ScanResult.ScanIssue... issues) {
        ScanResult scanResult = new ScanResult();
        scanResult.setIssues(List.of(issues));
        return scanResult;
    }

    private static ScanResult.ScanIssue issue(
            String type,
            String category,
            String description,
            String filePath,
            int lineStart,
            String severity,
            int scoreImpact,
            int confidence
    ) {
        ScanResult.ScanIssue issue = new ScanResult.ScanIssue();
        issue.setType(type);
        issue.setCategory(category);
        issue.setDescription(description);
        issue.setFilePath(filePath);
        issue.setLineStart(lineStart);
        issue.setSeverity(severity);
        issue.setScoreImpact(scoreImpact);
        issue.setConfidence(confidence);
        return issue;
    }
}
