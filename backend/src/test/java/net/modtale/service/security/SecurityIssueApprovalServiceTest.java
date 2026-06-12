package net.modtale.service.security;

import com.mongodb.client.result.UpdateResult;
import net.modtale.config.properties.AppSecurityProperties;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.service.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityIssueApprovalServiceTest {

    private final SecurityIssueClassificationService classificationService =
            new SecurityIssueClassificationService(securityProperties());
    private final SecurityIssueApprovalService approvalService =
            new SecurityIssueApprovalService(classificationService);

    @Test
    void approvalKeepsCompactBaselinesAndClearsHeavyScanResult() {
        ProjectVersion version = approvedVersion("version-1", "1.0.0");
        ScanResult.ScanIssue issue = issue();
        ScanResult scanResult = new ScanResult();
        scanResult.setIssues(new ArrayList<>(List.of(issue)));
        version.setScanResult(scanResult);

        approvalService.markIssuesAcceptedForApprovedVersion(version);

        assertNull(version.getScanResult());
        assertEquals(1, version.getApprovedIssueBaselines().size());
        ProjectVersion.ApprovedIssueBaseline baseline = version.getApprovedIssueBaselines().getFirst();
        assertTrue(baseline.getFingerprint().startsWith("si:"));
        assertTrue(baseline.getLooseFingerprint().startsWith("sl:"));
        assertEquals("HIGH", baseline.getSeverity());
        assertEquals(12, baseline.getScoreImpact());
        assertEquals(80, baseline.getConfidence());
        assertTrue(issue.isKnownIssue());
        assertTrue(issue.isResolved());
        assertFalse(issue.isEscalated());
    }

    @Test
    void storedBaselinesAreUsedAfterScanResultIsRemoved() {
        ProjectVersion version = approvedVersion("version-1", "1.0.0");
        version.setApprovedIssueBaselines(List.of(new ProjectVersion.ApprovedIssueBaseline(
                "si:test",
                "sl:test",
                "MEDIUM",
                7,
                60,
                1_768_091_200_000L
        )));

        Project project = new Project();
        project.setVersions(List.of(version));

        SecurityIssueBaselineService baselineService = new SecurityIssueBaselineService();
        SecurityIssueAnalysisService.BaselineIndex index = baselineService.collectApprovedIssueBaselines(
                project,
                null,
                new SecurityIssueEvaluationService(120)
        );

        assertNotNull(index.exact().get("si:test"));
        assertEquals("1.0.0", index.exact().get("si:test").versionNumber());
        assertNotNull(index.loose().get("sl:test"));
    }

    @Test
    void projectPruneLeavesUnapprovedVersionScanResultsInPlace() {
        ProjectVersion approved = approvedVersion("version-1", "1.0.0");
        ScanResult approvedScan = new ScanResult();
        approvedScan.setIssues(new ArrayList<>(List.of(issue())));
        approved.setScanResult(approvedScan);

        ProjectVersion pending = approvedVersion("version-2", "2.0.0");
        pending.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        ScanResult pendingScan = new ScanResult();
        pendingScan.setIssues(new ArrayList<>(List.of(issue())));
        pending.setScanResult(pendingScan);

        Project project = new Project();
        project.setVersions(List.of(approved, pending));

        assertEquals(1, approvalService.pruneApprovedScanResults(project));

        assertNull(approved.getScanResult());
        assertEquals(1, approved.getApprovedIssueBaselines().size());
        assertEquals(pendingScan, pending.getScanResult());
        assertNull(pending.getApprovedIssueBaselines());
    }

    @Test
    void cleanupPrunesApprovedScanResultsWithAtomicVersionUpdates() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        ProjectService projectService = mock(ProjectService.class);
        SecurityIssueAnalysisService analysisService = new SecurityIssueAnalysisService(
                classificationService,
                approvalService
        );
        ApprovedScanResultCleanupService cleanupService = new ApprovedScanResultCleanupService(
                mongoTemplate,
                projectService,
                analysisService
        );

        Project project = new Project();
        project.setId("project-1");
        ProjectVersion version = approvedVersion("version-1", "1.0.0");
        ScanResult scanResult = new ScanResult();
        scanResult.setIssues(new ArrayList<>(List.of(issue())));
        version.setScanResult(scanResult);
        project.setVersions(List.of(version));

        when(mongoTemplate.find(any(Query.class), eq(Project.class))).thenReturn(List.of(project));
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Project.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertEquals(1, cleanupService.pruneBatch(10));

        assertNull(version.getScanResult());
        assertEquals(1, version.getApprovedIssueBaselines().size());
        verify(projectService).evictProjectCache(project);
    }

    private static ProjectVersion approvedVersion(String id, String versionNumber) {
        ProjectVersion version = new ProjectVersion();
        version.setId(id);
        version.setVersionNumber(versionNumber);
        version.setReleaseDate("2026-01-01T00:00:00");
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        return version;
    }

    private static ScanResult.ScanIssue issue() {
        ScanResult.ScanIssue issue = new ScanResult.ScanIssue();
        issue.setType("dangerous-call");
        issue.setCategory("runtime");
        issue.setDescription("Uses a dangerous reflection call");
        issue.setFilePath("mods/example/Foo.class");
        issue.setLineStart(42);
        issue.setSeverity("HIGH");
        issue.setScoreImpact(12);
        issue.setConfidence(80);
        return issue;
    }

    private static AppSecurityProperties securityProperties() {
        return new AppSecurityProperties("test", 60, 120, 2, 12, 15, 120, 25, 2);
    }
}
