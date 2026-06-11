package net.modtale.service.security;

import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

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
        if (issues == null) return;

        for (ScanResult.ScanIssue issue : issues) {
            if (issue == null) continue;
            issue.setFingerprint(securityIssueClassificationService.fingerprint(issue));
            issue.setKnownIssue(true);
            issue.setEscalated(false);
            issue.setResolved(true);
            if (issue.getBaselineVersion() == null) {
                issue.setBaselineVersion(version.getVersionNumber());
            }
            if (issue.getBaselineSeverity() == null || issue.getBaselineSeverity().isBlank()) {
                issue.setBaselineSeverity(normalizeSeverity(issue.getSeverity()));
            }
            if (issue.getBaselineScoreImpact() <= 0) {
                issue.setBaselineScoreImpact(Math.max(0, issue.getScoreImpact()));
            }
        }

        scanResult.setKnownIssueCount(issues.size());
        scanResult.setNewIssueCount(0);
        scanResult.setEscalatedIssueCount(0);
    }

    private String normalizeSeverity(String severity) {
        if (severity == null || severity.isBlank()) return "LOW";
        String upper = severity.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "CRITICAL", "HIGH", "MEDIUM", "LOW" -> upper;
            default -> "LOW";
        };
    }
}
