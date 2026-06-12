package net.modtale.service.security.scan;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.modtale.config.properties.AppSecurityProperties;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import org.springframework.stereotype.Service;

@Service
public class ScanRoutingService {

    private final AppSecurityProperties securityProperties;

    public ScanRoutingService(AppSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public ScanResult createQueuedScanResult(int attempt, String note) {
        ScanResult pending = new ScanResult();
        pending.setStatus(ScanStatus.SCANNING);
        pending.setScanState("QUEUED");
        pending.setRiskScore(0);
        pending.setConfidenceScore(0);
        pending.setScanAttempt(Math.max(1, attempt));
        pending.setScanTimestamp(System.currentTimeMillis());

        List<String> notes = new ArrayList<>();
        if (note != null && !note.isBlank()) {
            notes.add(note);
        }
        pending.setReviewerNotes(notes);
        return pending;
    }

    public int nextScanAttempt(ScanResult existing) {
        if (existing == null || existing.getScanAttempt() <= 0) {
            return 1;
        }
        return existing.getScanAttempt() + 1;
    }

    public RoutingDecision decideRouting(
            ScanResult scanResult,
            SecurityIssueAnalysisService.ClassificationStats classification,
            boolean isManualRescan
    ) {
        String verdict = scanResult.getVerdict() == null ? "" : scanResult.getVerdict().toUpperCase();
        ScanStatus status = scanResult.getStatus();

        boolean block = "BLOCK".equals(verdict) || status == ScanStatus.INFECTED;
        boolean explicitReview = "REVIEW".equals(verdict)
                || status == ScanStatus.SUSPICIOUS
                || status == ScanStatus.FLAGGED
                || "UPSTREAM_UNAVAILABLE".equalsIgnoreCase(scanResult.getScanState())
                || hasRecoverableScannerErrors(scanResult);

        boolean knownOnly = classification.knownOnly();
        boolean hasEscalation = classification.escalatedIssueCount() > 0;

        if (isManualRescan) {
            if (block || (explicitReview && (!knownOnly || hasEscalation))) {
                return new RoutingDecision(RoutingAction.REQUIRE_REVIEW, 0);
            }
            return new RoutingDecision(RoutingAction.APPROVE_NOW, 0);
        }

        if (block) {
            return new RoutingDecision(RoutingAction.REQUIRE_REVIEW, 0);
        }

        if (explicitReview) {
            if (knownOnly && !hasEscalation && !"UPSTREAM_UNAVAILABLE".equalsIgnoreCase(scanResult.getScanState())) {
                return new RoutingDecision(
                        RoutingAction.SCHEDULE,
                        randomDelay(securityProperties.knownRiskDelayMinutesMin(), securityProperties.knownRiskDelayMinutesMax())
                );
            }
            return new RoutingDecision(RoutingAction.REQUIRE_REVIEW, 0);
        }

        long holdDelayMinutes = holdDelayFromWarden(scanResult);
        long randomizedDelay = randomDelay(
                securityProperties.autoApproveDelayMinutesMin(),
                securityProperties.autoApproveDelayMinutesMax()
        );
        return new RoutingDecision(RoutingAction.SCHEDULE, Math.max(holdDelayMinutes, randomizedDelay));
    }

    public long scanTimeoutMillis() {
        return Math.max(1, securityProperties.scanTimeoutMinutes()) * 60_000L;
    }

    public int scanMaxRetries() {
        return securityProperties.scanMaxRetries();
    }

    public ScanResult buildPipelineErrorResult(Exception exception, String filename, int attempt) {
        ScanResult failed = new ScanResult();
        failed.setStatus(ScanStatus.SUSPICIOUS);
        failed.setVerdict("REVIEW");
        failed.setRiskLevel("HIGH");
        failed.setScanState("PIPELINE_ERROR");
        failed.setRiskScore(50);
        failed.setConfidenceScore(30);
        failed.setScanTimestamp(System.currentTimeMillis());
        failed.setScanAttempt(Math.max(1, attempt));

        ScanResult.ScanIssue issue = new ScanResult.ScanIssue();
        issue.setSeverity("MEDIUM");
        issue.setType("ScanPipelineError");
        issue.setCategory("System");
        issue.setDescription("Scan pipeline failed: "
                + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        issue.setFilePath(filename == null ? "uploaded-artifact" : filename);
        issue.setLineStart(-1);
        issue.setLineEnd(-1);
        issue.setScoreImpact(6);
        issue.setConfidence(35);
        issue.setReviewPriority("P1");

        failed.setIssues(List.of(issue));
        failed.setReviewerNotes(List.of(
                "Scan pipeline encountered an internal error.",
                "Manual review is required before publishing."
        ));
        return failed;
    }

    public ScanResult buildScanTimeoutResult(String filename, int attempt) {
        ScanResult timedOut = new ScanResult();
        timedOut.setStatus(ScanStatus.SUSPICIOUS);
        timedOut.setVerdict("REVIEW");
        timedOut.setRiskLevel("HIGH");
        timedOut.setScanState("SCAN_TIMEOUT");
        timedOut.setRiskScore(52);
        timedOut.setConfidenceScore(28);
        timedOut.setScanTimestamp(System.currentTimeMillis());
        timedOut.setScanAttempt(Math.max(1, attempt));

        ScanResult.ScanIssue issue = new ScanResult.ScanIssue();
        issue.setSeverity("MEDIUM");
        issue.setType("ScanTimeout");
        issue.setCategory("System");
        issue.setDescription("Scan exceeded timeout window and could not be completed automatically.");
        issue.setFilePath(filename == null ? "uploaded-artifact" : filename);
        issue.setLineStart(-1);
        issue.setLineEnd(-1);
        issue.setScoreImpact(7);
        issue.setConfidence(40);
        issue.setReviewPriority("P1");

        timedOut.setIssues(List.of(issue));
        timedOut.setReviewerNotes(List.of(
                "The scan timed out after repeated attempts.",
                "Manual review is required before publishing."
        ));
        return timedOut;
    }

    private long holdDelayFromWarden(ScanResult scanResult) {
        long holdUntil = scanResult.getHoldUntilTimestamp();
        if (holdUntil <= 0) return 0;
        long remainingMs = holdUntil - System.currentTimeMillis();
        if (remainingMs <= 0) return 0;
        return (long) Math.ceil(remainingMs / 60000.0);
    }

    private boolean hasRecoverableScannerErrors(ScanResult scanResult) {
        ScanResult.ScanSummary summary = scanResult.getSummary();
        if (summary == null) return false;
        return summary.getRecoverableErrors() > 0
                || summary.getOversizedEntriesSkipped() > 0
                || summary.getNestedArchiveReadFailures() > 0;
    }

    private long randomDelay(long minMinutes, long maxMinutes) {
        long min = Math.max(1, Math.min(minMinutes, maxMinutes));
        long max = Math.max(min, Math.max(minMinutes, maxMinutes));
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    public enum RoutingAction {
        REQUIRE_REVIEW,
        SCHEDULE,
        APPROVE_NOW
    }

    public record RoutingDecision(RoutingAction action, long delayMinutes) {
    }
}
