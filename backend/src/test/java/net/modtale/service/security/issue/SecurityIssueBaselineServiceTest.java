package net.modtale.service.security.issue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityIssueBaselineServiceTest {

    private SecurityIssueBaselineService service;
    private SecurityIssueEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        service = new SecurityIssueBaselineService();
        evaluationService = new SecurityIssueEvaluationService(120);
    }

    @Test
    void collectApprovedIssueBaselinesReturnsEmptyForMissingProjectsOrVersions() {
        assertTrue(service.collectApprovedIssueBaselines(null, null, evaluationService).exact().isEmpty());

        Project project = new Project();
        project.setVersions(null);

        assertTrue(service.collectApprovedIssueBaselines(project, null, evaluationService).loose().isEmpty());
    }

    @Test
    void collectApprovedIssueBaselinesUsesStoredBaselinesAndSkipsExcludedOrUnapprovedVersions() {
        ProjectVersion approved = approvedVersion("version-1", "1.0.0");
        approved.setApprovedIssueBaselines(List.of(
                new ProjectVersion.ApprovedIssueBaseline("si:one", "", "wat", -5, -10, 123L),
                new ProjectVersion.ApprovedIssueBaseline(" ", "ignored", "HIGH", 9, 80, 456L)
        ));
        ProjectVersion excluded = approvedVersion("version-2", "2.0.0");
        excluded.setApprovedIssueBaselines(List.of(new ProjectVersion.ApprovedIssueBaseline(
                "si:excluded", "sl:excluded", "HIGH", 9, 80, 456L
        )));
        ProjectVersion pending = approvedVersion("version-3", "3.0.0");
        pending.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        pending.setApprovedIssueBaselines(List.of(new ProjectVersion.ApprovedIssueBaseline(
                "si:pending", "sl:pending", "HIGH", 9, 80, 456L
        )));
        Project project = new Project();
        project.setVersions(new ArrayList<>(List.of(approved, excluded, pending)));
        project.getVersions().add(null);

        SecurityIssueAnalysisService.BaselineIndex index =
                service.collectApprovedIssueBaselines(project, "version-2", evaluationService);

        SecurityIssueAnalysisService.IssueBaseline baseline = index.exact().get("si:one");
        assertNotNull(baseline);
        assertEquals("si:one", baseline.looseFingerprint());
        assertEquals("LOW", baseline.severity());
        assertEquals(0, baseline.scoreImpact());
        assertEquals(0, baseline.confidence());
        assertEquals(123L, baseline.lastSeenTimestamp());
        assertTrue(!index.exact().containsKey("si:excluded"));
        assertTrue(!index.exact().containsKey("si:pending"));
    }

    @Test
    void collectApprovedIssueBaselinesBuildsAndMergesBaselinesFromScanResults() {
        ScanResult.ScanIssue olderLow = issue("Runtime", "Reflection", "Danger 1", "Foo.class", 1, "LOW", 3, 20);
        ScanResult.ScanIssue newerHigh = issue("Runtime", "Reflection", "Danger 2", "Foo.class", 1, "HIGH", 8, 70);
        ProjectVersion older = approvedVersion("version-1", "1.0.0");
        older.setReleaseDate("2026-01-01T00:00:00Z");
        older.setScanResult(scan(olderLow));
        ProjectVersion newer = approvedVersion("version-2", "2.0.0");
        newer.setReleaseDate("2026-02-01T00:00:00Z");
        newer.setScanResult(scan(newerHigh));
        Project project = new Project();
        project.setVersions(List.of(older, newer));

        SecurityIssueAnalysisService.BaselineIndex index =
                service.collectApprovedIssueBaselines(project, null, evaluationService);

        SecurityIssueAnalysisService.IssueBaseline merged =
                index.loose().get(evaluationService.looseFingerprint(olderLow));
        assertNotNull(merged);
        assertEquals("HIGH", merged.severity());
        assertEquals(8, merged.scoreImpact());
        assertEquals(70, merged.confidence());
        assertEquals("version-2", merged.versionId());
        assertEquals("2.0.0", merged.versionNumber());
        assertEquals(2, merged.seenCount());
    }

    private static ProjectVersion approvedVersion(String id, String versionNumber) {
        ProjectVersion version = new ProjectVersion();
        version.setId(id);
        version.setVersionNumber(versionNumber);
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        version.setReleaseDate(Instant.ofEpochMilli(0).toString());
        return version;
    }

    private static ScanResult scan(ScanResult.ScanIssue issue) {
        ScanResult scanResult = new ScanResult();
        scanResult.setIssues(List.of(issue));
        return scanResult;
    }

    private static ScanResult.ScanIssue issue(
            String type,
            String category,
            String description,
            String filePath,
            int lineStart,
            String severity,
            int scoreImpact,
            int confidence
    ) {
        ScanResult.ScanIssue issue = new ScanResult.ScanIssue();
        issue.setType(type);
        issue.setCategory(category);
        issue.setDescription(description);
        issue.setFilePath(filePath);
        issue.setLineStart(lineStart);
        issue.setSeverity(severity);
        issue.setScoreImpact(scoreImpact);
        issue.setConfidence(confidence);
        return issue;
    }
}
