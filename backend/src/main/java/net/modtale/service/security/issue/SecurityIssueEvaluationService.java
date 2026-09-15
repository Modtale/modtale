package net.modtale.service.security.issue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;

final class SecurityIssueEvaluationService {

    private final long baselineConfidenceDecayDays;

    SecurityIssueEvaluationService(long baselineConfidenceDecayDays) {
        this.baselineConfidenceDecayDays = baselineConfidenceDecayDays;
    }

    SecurityIssueAnalysisService.ClassificationStats annotateAgainstBaselines(
            ScanResult scanResult,
            SecurityIssueAnalysisService.BaselineIndex approvedBaselines
    ) {
        normalizeScanResult(scanResult);

        SecurityIssueAnalysisService.BaselineIndex baselineIndex = approvedBaselines == null
                ? new SecurityIssueAnalysisService.BaselineIndex(new HashMap<>(), new HashMap<>())
                : approvedBaselines;

        int known = 0;
        int fresh = 0;
        int escalated = 0;

        long now = Instant.now().toEpochMilli();
        long decayMs = Math.max(1, baselineConfidenceDecayDays) * 86_400_000L;

        for (ScanResult.ScanIssue issue : scanResult.getIssues()) {
            if (issue == null) {
                continue;
            }

            String fingerprint = fingerprint(issue);
            String looseFingerprint = looseFingerprint(issue);
            issue.setFingerprint(fingerprint);
            boolean alwaysReview = isAlwaysReviewIssue(issue);

            SecurityIssueAnalysisService.IssueBaseline baseline = baselineIndex.exact().get(fingerprint);
            boolean looseMatch = false;
            if (baseline == null) {
                baseline = baselineIndex.loose().get(looseFingerprint);
                looseMatch = baseline != null;
            }

            if (baseline == null) {
                issue.setKnownIssue(false);
                issue.setEscalated(false);
                issue.setResolved(false);
                issue.setBaselineVersion(null);
                issue.setBaselineScoreImpact(0);
                issue.setBaselineSeverity(null);
                fresh++;
                continue;
            }

            issue.setKnownIssue(true);
            issue.setBaselineVersion(baseline.versionNumber());
            issue.setBaselineScoreImpact(baseline.scoreImpact());
            issue.setBaselineSeverity(baseline.severity());
            known++;

            boolean severityEscalation =
                    severityRank(normalizeSeverity(issue.getSeverity())) > severityRank(baseline.severity());
            boolean scoreEscalation = issue.getScoreImpact() > baseline.scoreImpact() + 6;
            boolean confidenceEscalation = issue.getConfidence() > baseline.confidence() + 12;
            boolean staleBaseline = baseline.lastSeenTimestamp() > 0 && (now - baseline.lastSeenTimestamp()) > decayMs;
            boolean weakHistory = baseline.seenCount() <= 1
                    && (issue.getScoreImpact() >= 8 || severityRank(normalizeSeverity(issue.getSeverity())) >= 3);

            boolean escalatedIssue = severityEscalation
                    || scoreEscalation
                    || confidenceEscalation
                    || staleBaseline
                    || weakHistory
                    || looseMatch
                    || alwaysReview;

            issue.setEscalated(escalatedIssue);
            issue.setResolved(!escalatedIssue);

            if (escalatedIssue) {
                escalated++;
            }
        }

        scanResult.setKnownIssueCount(known);
        scanResult.setNewIssueCount(fresh);
        scanResult.setEscalatedIssueCount(escalated);

        return new SecurityIssueAnalysisService.ClassificationStats(
                known,
                fresh,
                escalated,
                known > 0 && fresh == 0 && escalated == 0
        );
    }

    void normalizeScanResult(ScanResult scanResult) {
        if (scanResult == null) {
            return;
        }

        if (scanResult.getIssues() == null) {
            scanResult.setIssues(new ArrayList<>());
        }
        if (scanResult.getReviewerNotes() == null) {
            scanResult.setReviewerNotes(new ArrayList<>());
        }
        if (scanResult.getReviewTargets() == null) {
            scanResult.setReviewTargets(new ArrayList<>());
        }
        if (scanResult.getScanTimestamp() <= 0) {
            scanResult.setScanTimestamp(Instant.now().toEpochMilli());
        }
        if (scanResult.getScanAttempt() <= 0) {
            scanResult.setScanAttempt(1);
        }

        if (scanResult.getStatus() == null) {
            if ("BLOCK".equalsIgnoreCase(scanResult.getVerdict())) {
                scanResult.setStatus(ScanStatus.INFECTED);
            } else if ("REVIEW".equalsIgnoreCase(scanResult.getVerdict())) {
                scanResult.setStatus(ScanStatus.SUSPICIOUS);
            } else {
                scanResult.setStatus(ScanStatus.CLEAN);
            }
        }
    }

    String fingerprint(ScanResult.ScanIssue issue) {
        if (issue == null) {
            return "issue:unknown";
        }

        String type = normalizeToken(issue.getType());
        String category = normalizeToken(issue.getCategory());
        String file = normalizePathForFingerprint(issue.getFilePath());
        String description = normalizeDescription(issue.getDescription());
        String lineBucket = issue.getLineStart() > 0 ? Integer.toString(issue.getLineStart() / 8) : "na";

        String payload = String.join("|", type, category, file, lineBucket, description);
        return "si:" + hash(payload);
    }

    String looseFingerprint(ScanResult.ScanIssue issue) {
        if (issue == null) {
            return "issue:unknown";
        }

        String type = normalizeToken(issue.getType());
        String category = normalizeToken(issue.getCategory());
        String description = normalizeDescription(issue.getDescription());

        return "sl:" + hash(String.join("|", type, category, description));
    }

    String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "LOW";
        }
        String upper = severity.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "CRITICAL", "HIGH", "MEDIUM", "LOW" -> upper;
            default -> "LOW";
        };
    }

    private String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            return "na";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
    }

    private String normalizePathForFingerprint(String path) {
        if (path == null || path.isBlank()) {
            return "archive-root";
        }

        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String[] nestedParts = normalized.split("->");
        String tail = nestedParts[nestedParts.length - 1].trim();
        if (tail.isBlank()) {
            tail = normalized;
        }

        String[] segments = tail.split("/");
        StringBuilder canonical = new StringBuilder();
        int start = Math.max(0, segments.length - 4);
        for (int i = start; i < segments.length; i++) {
            String segment = segments[i].replaceAll("\\s+", "");
            if (segment.isBlank()) {
                continue;
            }
            if (canonical.length() > 0) {
                canonical.append('/');
            }
            canonical.append(segment);
        }

        String out = canonical.length() == 0 ? "archive-root" : canonical.toString();
        return out.replaceAll("[^a-z0-9./$:_-]+", "");
    }

    private String normalizeDescription(String text) {
        if (text == null || text.isBlank()) {
            return "na";
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("0x[0-9a-f]+", "0x#");
        normalized = normalized.replaceAll("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b", "ip#");
        normalized = normalized.replaceAll("\\d+", "#");
        normalized = normalized.replaceAll("[^a-z0-9#:/._-]+", " ");
        normalized = normalized.trim().replaceAll("\\s{2,}", " ");
        return normalized;
    }

    private String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) {
                out.append(String.format("%02x", b));
            }
            return out.substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private boolean isAlwaysReviewIssue(ScanResult.ScanIssue issue) {
        if (issue == null || issue.getReviewCadence() == null) {
            return false;
        }
        return "ALWAYS".equalsIgnoreCase(issue.getReviewCadence());
    }
}
