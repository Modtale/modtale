package net.modtale.service.security;

import net.modtale.config.properties.AppSecurityProperties;
import net.modtale.model.project.Project;
import net.modtale.model.project.ScanResult;
import org.springframework.stereotype.Service;

@Service
public class SecurityIssueClassificationService {

    private final SecurityIssueBaselineService baselineService;
    private final SecurityIssueEvaluationService evaluationService;

    public SecurityIssueClassificationService(AppSecurityProperties securityProperties) {
        this.baselineService = new SecurityIssueBaselineService();
        this.evaluationService = new SecurityIssueEvaluationService(
                securityProperties.baselineConfidenceDecayDays()
        );
    }

    public SecurityIssueAnalysisService.BaselineIndex collectApprovedIssueBaselines(Project project, String excludeVersionId) {
        return baselineService.collectApprovedIssueBaselines(project, excludeVersionId, evaluationService);
    }

    public SecurityIssueAnalysisService.ClassificationStats annotateAgainstBaselines(
            ScanResult scanResult,
            SecurityIssueAnalysisService.BaselineIndex approvedBaselines
    ) {
        return evaluationService.annotateAgainstBaselines(scanResult, approvedBaselines);
    }

    public void normalizeScanResult(ScanResult scanResult) {
        evaluationService.normalizeScanResult(scanResult);
    }

    public String fingerprint(ScanResult.ScanIssue issue) {
        return evaluationService.fingerprint(issue);
    }

    public String looseFingerprint(ScanResult.ScanIssue issue) {
        return evaluationService.looseFingerprint(issue);
    }

    public String normalizeSeverity(String severity) {
        return evaluationService.normalizeSeverity(severity);
    }
}
