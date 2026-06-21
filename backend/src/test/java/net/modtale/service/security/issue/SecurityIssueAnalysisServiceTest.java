package net.modtale.service.security.issue;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityIssueAnalysisServiceTest {

    private SecurityIssueClassificationService classificationService;
    private SecurityIssueApprovalService approvalService;
    private SecurityIssueAnalysisService service;

    @BeforeEach
    void setUp() {
        classificationService = mock(SecurityIssueClassificationService.class);
        approvalService = mock(SecurityIssueApprovalService.class);
        service = new SecurityIssueAnalysisService(classificationService, approvalService);
    }

    @Test
    void delegatesClassificationAndApprovalOperations() {
        Project project = new Project();
        ProjectVersion version = new ProjectVersion();
        ScanResult scanResult = new ScanResult();
        SecurityIssueAnalysisService.BaselineIndex baselineIndex =
                new SecurityIssueAnalysisService.BaselineIndex(java.util.Map.of(), java.util.Map.of());

        when(classificationService.collectApprovedIssueBaselines(project, "version-1")).thenReturn(baselineIndex);

        service.collectApprovedIssueBaselines(project, "version-1");
        service.annotateAgainstBaselines(scanResult, baselineIndex);
        service.normalizeScanResult(scanResult);
        service.fingerprint(new ScanResult.ScanIssue());
        service.markIssuesAcceptedForApprovedVersion(version);
        service.pruneApprovedScanResults(project);

        verify(classificationService).collectApprovedIssueBaselines(project, "version-1");
        verify(classificationService).annotateAgainstBaselines(scanResult, baselineIndex);
        verify(classificationService).normalizeScanResult(scanResult);
        verify(classificationService).fingerprint(org.mockito.ArgumentMatchers.any(ScanResult.ScanIssue.class));
        verify(approvalService).markIssuesAcceptedForApprovedVersion(version);
        verify(approvalService).pruneApprovedScanResults(project);
    }
}
