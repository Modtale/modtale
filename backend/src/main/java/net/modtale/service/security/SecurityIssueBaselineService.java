package net.modtale.service.security;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
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

            ScanResult scanResult = version.getScanResult();
            if (scanResult == null || scanResult.getIssues() == null) {
                continue;
            }

            long approvedAt = parseTimestamp(version.getReleaseDate());
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
