package net.modtale.service.security;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SecurityIssueBaselineService {

    SecurityIssueAnalysisService.BaselineIndex collectApprovedIssueBaselines(
            Project project,
            String excludeVersionId,
            SecurityIssueEvaluationService evaluationService
    ) {
        Map<String, SecurityIssueAnalysisService.IssueBaseline> exactBaselines = new HashMap<>();
        Map<String, SecurityIssueAnalysisService.IssueBaseline> looseBaselines = new HashMap<>();

        if (project == null || project.getVersions() == null) {
            return new SecurityIssueAnalysisService.BaselineIndex(exactBaselines, looseBaselines);
        }

        for (ProjectVersion version : project.getVersions()) {
            if (version == null) {
                continue;
            }
            if (excludeVersionId != null && excludeVersionId.equals(version.getId())) {
                continue;
            }
            if (version.getReviewStatus() != ProjectVersion.ReviewStatus.APPROVED) {
                continue;
            }

            long approvedAt = parseTimestamp(version.getReleaseDate());
            if (collectStoredBaselines(version, approvedAt, exactBaselines, looseBaselines)) {
                continue;
            }

            ScanResult scanResult = version.getScanResult();
            if (scanResult == null || scanResult.getIssues() == null) {
                continue;
            }

            for (ScanResult.ScanIssue issue : scanResult.getIssues()) {
                if (issue == null) {
                    continue;
                }

                String fingerprint = evaluationService.fingerprint(issue);
                String looseFingerprint = evaluationService.looseFingerprint(issue);

                SecurityIssueAnalysisService.IssueBaseline candidate = new SecurityIssueAnalysisService.IssueBaseline(
                        fingerprint,
                        looseFingerprint,
                        evaluationService.normalizeSeverity(issue.getSeverity()),
                        Math.max(0, issue.getScoreImpact()),
                        Math.max(0, issue.getConfidence()),
                        version.getId(),
                        version.getVersionNumber(),
                        approvedAt,
                        1
                );

                exactBaselines.merge(fingerprint, candidate, SecurityIssueAnalysisService.IssueBaseline::mergeWith);
                looseBaselines.merge(looseFingerprint, candidate, SecurityIssueAnalysisService.IssueBaseline::mergeWith);
            }
        }

        return new SecurityIssueAnalysisService.BaselineIndex(exactBaselines, looseBaselines);
    }

    private boolean collectStoredBaselines(
            ProjectVersion version,
            long approvedAt,
            Map<String, SecurityIssueAnalysisService.IssueBaseline> exactBaselines,
            Map<String, SecurityIssueAnalysisService.IssueBaseline> looseBaselines
    ) {
        List<ProjectVersion.ApprovedIssueBaseline> storedBaselines = version.getApprovedIssueBaselines();
        if (storedBaselines == null || storedBaselines.isEmpty()) {
            return false;
        }

        for (ProjectVersion.ApprovedIssueBaseline storedBaseline : storedBaselines) {
            if (storedBaseline == null || isBlank(storedBaseline.getFingerprint())) {
                continue;
            }

            String looseFingerprint = isBlank(storedBaseline.getLooseFingerprint())
                    ? storedBaseline.getFingerprint()
                    : storedBaseline.getLooseFingerprint();
            SecurityIssueAnalysisService.IssueBaseline candidate = new SecurityIssueAnalysisService.IssueBaseline(
                    storedBaseline.getFingerprint(),
                    looseFingerprint,
                    normalizeSeverity(storedBaseline.getSeverity()),
                    Math.max(0, storedBaseline.getScoreImpact()),
                    Math.max(0, storedBaseline.getConfidence()),
                    version.getId(),
                    version.getVersionNumber(),
                    storedBaseline.getApprovedAt() > 0 ? storedBaseline.getApprovedAt() : approvedAt,
                    1
            );

            exactBaselines.merge(candidate.fingerprint(), candidate, SecurityIssueAnalysisService.IssueBaseline::mergeWith);
            looseBaselines.merge(candidate.looseFingerprint(), candidate, SecurityIssueAnalysisService.IssueBaseline::mergeWith);
        }

        return true;
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "LOW";
        }
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> "CRITICAL";
            case "HIGH" -> "HIGH";
            case "MEDIUM" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private long parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return 0;
        }

        try {
            return Instant.parse(timestamp).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return 0;
        }
    }
}
