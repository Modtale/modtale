package net.modtale.service.security;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SecurityIssueAnalysisService {

    private final SecurityIssueClassificationService securityIssueClassificationService;
    private final SecurityIssueApprovalService securityIssueApprovalService;

    public SecurityIssueAnalysisService(
            SecurityIssueClassificationService securityIssueClassificationService,
            SecurityIssueApprovalService securityIssueApprovalService
    ) {
        this.securityIssueClassificationService = securityIssueClassificationService;
        this.securityIssueApprovalService = securityIssueApprovalService;
    }

    public BaselineIndex collectApprovedIssueBaselines(Project project, String excludeVersionId) {
        return securityIssueClassificationService.collectApprovedIssueBaselines(project, excludeVersionId);
    }

    public ClassificationStats annotateAgainstBaselines(
            ScanResult scanResult,
            BaselineIndex approvedBaselines
    ) {
        return securityIssueClassificationService.annotateAgainstBaselines(scanResult, approvedBaselines);
    }

    public void markIssuesAcceptedForApprovedVersion(ProjectVersion version) {
        securityIssueApprovalService.markIssuesAcceptedForApprovedVersion(version);
    }

    public int pruneApprovedScanResults(Project project) {
        return securityIssueApprovalService.pruneApprovedScanResults(project);
    }

    public void normalizeScanResult(ScanResult scanResult) {
        securityIssueClassificationService.normalizeScanResult(scanResult);
    }

    public String fingerprint(ScanResult.ScanIssue issue) {
        return securityIssueClassificationService.fingerprint(issue);
    }

    public record IssueBaseline(
            String fingerprint,
            String looseFingerprint,
            String severity,
            int scoreImpact,
            int confidence,
            String versionId,
            String versionNumber,
            long lastSeenTimestamp,
            int seenCount
    ) {
        IssueBaseline mergeWith(IssueBaseline other) {
            if (other == null) return this;

            int mergedSeverityRank = Math.max(rank(this.severity), rank(other.severity));
            String mergedSeverity = severityFromRank(mergedSeverityRank);

            boolean thisLatest = this.lastSeenTimestamp >= other.lastSeenTimestamp;
            return new IssueBaseline(
                    this.fingerprint,
                    this.looseFingerprint,
                    mergedSeverity,
                    Math.max(this.scoreImpact, other.scoreImpact),
                    Math.max(this.confidence, other.confidence),
                    thisLatest ? this.versionId : other.versionId,
                    thisLatest ? this.versionNumber : other.versionNumber,
                    Math.max(this.lastSeenTimestamp, other.lastSeenTimestamp),
                    this.seenCount + other.seenCount
            );
        }

        private static int rank(String severity) {
            return switch (severity) {
                case "CRITICAL" -> 4;
                case "HIGH" -> 3;
                case "MEDIUM" -> 2;
                default -> 1;
            };
        }

        private static String severityFromRank(int rank) {
            return switch (rank) {
                case 4 -> "CRITICAL";
                case 3 -> "HIGH";
                case 2 -> "MEDIUM";
                default -> "LOW";
            };
        }
    }

    public record BaselineIndex(
            Map<String, IssueBaseline> exact,
            Map<String, IssueBaseline> loose
    ) {}

    public record ClassificationStats(
            int knownIssueCount,
            int newIssueCount,
            int escalatedIssueCount,
            boolean knownOnly
    ) {}
}
