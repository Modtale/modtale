package net.modtale.service.security;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class SecurityIssueApprovalService {

    private final SecurityIssueClassificationService securityIssueClassificationService;

    public SecurityIssueApprovalService(SecurityIssueClassificationService securityIssueClassificationService) {
        this.securityIssueClassificationService = securityIssueClassificationService;
    }

    public void markIssuesAcceptedForApprovedVersion(ProjectVersion version) {
        if (version == null || version.getScanResult() == null) return;
        ScanResult scanResult = version.getScanResult();
        securityIssueClassificationService.normalizeScanResult(scanResult);

        List<ScanResult.ScanIssue> issues = scanResult.getIssues();
        List<ProjectVersion.ApprovedIssueBaseline> approvedIssueBaselines = new ArrayList<>();
        long approvedAt = approvedAt(version);

        for (ScanResult.ScanIssue issue : issues) {
            if (issue == null) continue;
            String fingerprint = securityIssueClassificationService.fingerprint(issue);
            String looseFingerprint = securityIssueClassificationService.looseFingerprint(issue);
            String severity = securityIssueClassificationService.normalizeSeverity(issue.getSeverity());

            issue.setFingerprint(fingerprint);
            issue.setKnownIssue(true);
            issue.setEscalated(false);
            issue.setResolved(true);
            if (issue.getBaselineVersion() == null) {
                issue.setBaselineVersion(version.getVersionNumber());
            }
            if (issue.getBaselineSeverity() == null || issue.getBaselineSeverity().isBlank()) {
                issue.setBaselineSeverity(severity);
            }
            if (issue.getBaselineScoreImpact() <= 0) {
                issue.setBaselineScoreImpact(Math.max(0, issue.getScoreImpact()));
            }

            approvedIssueBaselines.add(new ProjectVersion.ApprovedIssueBaseline(
                    fingerprint,
                    looseFingerprint,
                    severity,
                    Math.max(0, issue.getScoreImpact()),
                    Math.max(0, issue.getConfidence()),
                    approvedAt
            ));
        }

        scanResult.setKnownIssueCount(issues.size());
        scanResult.setNewIssueCount(0);
        scanResult.setEscalatedIssueCount(0);
        version.setApprovedIssueBaselines(approvedIssueBaselines);
        version.setScanResult(null);
    }

    public int pruneApprovedScanResults(Project project) {
        if (project == null || project.getVersions() == null) {
            return 0;
        }

        int pruned = 0;
        for (ProjectVersion version : project.getVersions()) {
            if (version == null
                    || version.getReviewStatus() != ProjectVersion.ReviewStatus.APPROVED
                    || version.getScanResult() == null) {
                continue;
            }
            markIssuesAcceptedForApprovedVersion(version);
            pruned++;
        }
        return pruned;
    }

    private long approvedAt(ProjectVersion version) {
        long parsedReleaseDate = parseTimestamp(version.getReleaseDate());
        return parsedReleaseDate > 0 ? parsedReleaseDate : Instant.now().toEpochMilli();
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
