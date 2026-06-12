package net.modtale.service.security.scan;

import net.modtale.config.properties.AppSecurityProperties;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanRoutingServiceTest {

    private ScanRoutingService service;

    @BeforeEach
    void setUp() {
        service = new ScanRoutingService(new AppSecurityProperties("secret", 60, 120, 2, 4, 15, 20, 25, 2));
    }

    @Test
    void createQueuedScanResultNormalizesAttemptsAndOptionalNotes() {
        ScanResult result = service.createQueuedScanResult(0, "Manual rescan requested.");

        assertEquals(ScanStatus.SCANNING, result.getStatus());
        assertEquals("QUEUED", result.getScanState());
        assertEquals(1, result.getScanAttempt());
        assertEquals(0, result.getRiskScore());
        assertEquals(0, result.getConfidenceScore());
        assertEquals(java.util.List.of("Manual rescan requested."), result.getReviewerNotes());
    }

    @Test
    void nextScanAttemptStartsAtOneAndIncrementsExistingAttempts() {
        ScanResult existing = new ScanResult();
        existing.setScanAttempt(3);

        assertEquals(1, service.nextScanAttempt(null));
        assertEquals(4, service.nextScanAttempt(existing));
    }

    @Test
    void decideRoutingRequiresReviewForBlocksAndEscalatedManualReviews() {
        ScanResult block = scan("BLOCK", ScanStatus.CLEAN, null);
        ScanResult manualReview = scan("REVIEW", ScanStatus.SUSPICIOUS, null);

        assertEquals(
                ScanRoutingService.RoutingAction.REQUIRE_REVIEW,
                service.decideRouting(block, stats(false, 0), false).action()
        );
        assertEquals(
                ScanRoutingService.RoutingAction.REQUIRE_REVIEW,
                service.decideRouting(manualReview, stats(false, 1), true).action()
        );
    }

    @Test
    void decideRoutingSchedulesKnownOnlyReviewButApprovesCleanManualRescans() {
        ScanResult review = scan("REVIEW", ScanStatus.SUSPICIOUS, null);
        ScanResult clean = scan("ALLOW", ScanStatus.CLEAN, null);

        ScanRoutingService.RoutingDecision reviewDecision = service.decideRouting(review, stats(true, 0), false);
        ScanRoutingService.RoutingDecision manualDecision = service.decideRouting(clean, stats(false, 0), true);

        assertEquals(ScanRoutingService.RoutingAction.SCHEDULE, reviewDecision.action());
        assertTrue(reviewDecision.delayMinutes() >= 15 && reviewDecision.delayMinutes() <= 20);
        assertEquals(ScanRoutingService.RoutingAction.APPROVE_NOW, manualDecision.action());
    }

    @Test
    void decideRoutingSchedulesCleanScansUsingWardenHoldWhenItIsLongerThanRandomDelay() {
        ScanResult clean = scan("ALLOW", ScanStatus.CLEAN, null);
        clean.setHoldUntilTimestamp(System.currentTimeMillis() + 30 * 60_000L);

        ScanRoutingService.RoutingDecision decision = service.decideRouting(clean, stats(false, 0), false);

        assertEquals(ScanRoutingService.RoutingAction.SCHEDULE, decision.action());
        assertTrue(decision.delayMinutes() >= 29);
    }

    @Test
    void decideRoutingTreatsRecoverableScannerErrorsAsExplicitReview() {
        ScanResult scan = scan("ALLOW", ScanStatus.CLEAN, null);
        ScanResult.ScanSummary summary = new ScanResult.ScanSummary();
        summary.setRecoverableErrors(1);
        scan.setSummary(summary);

        assertEquals(
                ScanRoutingService.RoutingAction.REQUIRE_REVIEW,
                service.decideRouting(scan, stats(false, 0), false).action()
        );
    }

    @Test
    void buildsPipelineAndTimeoutResultsWithManualReviewSignals() {
        ScanResult pipeline = service.buildPipelineErrorResult(new IllegalStateException("boom"), "mod.jar", 0);
        ScanResult timeout = service.buildScanTimeoutResult(null, 3);

        assertEquals(ScanStatus.SUSPICIOUS, pipeline.getStatus());
        assertEquals("PIPELINE_ERROR", pipeline.getScanState());
        assertEquals(1, pipeline.getScanAttempt());
        assertEquals("mod.jar", pipeline.getIssues().getFirst().getFilePath());
        assertEquals("SCAN_TIMEOUT", timeout.getScanState());
        assertEquals(3, timeout.getScanAttempt());
        assertEquals("uploaded-artifact", timeout.getIssues().getFirst().getFilePath());
    }

    @Test
    void exposesTimeoutAndRetrySettingsWithMinimums() {
        assertEquals(25 * 60_000L, service.scanTimeoutMillis());
        assertEquals(2, service.scanMaxRetries());
    }

    private static ScanResult scan(String verdict, ScanStatus status, String state) {
        ScanResult scanResult = new ScanResult();
        scanResult.setVerdict(verdict);
        scanResult.setStatus(status);
        scanResult.setScanState(state);
        return scanResult;
    }

    private static SecurityIssueAnalysisService.ClassificationStats stats(boolean knownOnly, int escalated) {
        return new SecurityIssueAnalysisService.ClassificationStats(knownOnly ? 1 : 0, knownOnly ? 0 : 1, escalated, knownOnly);
    }
}
