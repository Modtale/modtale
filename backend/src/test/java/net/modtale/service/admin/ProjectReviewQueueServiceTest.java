package net.modtale.service.admin;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectReviewQueueServiceTest {

    private ProjectReviewQueueService projectReviewQueueService;
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        projectReviewQueueService = new ProjectReviewQueueService(mongoTemplate);
    }

    @Test
    void getVerificationQueueFiltersOutScanningVersionsDeduplicatesAndSorts() {
        Project pendingProject = project("pending-1", "Pending", ProjectStatus.PENDING);
        pendingProject.setUpdatedAt("2024-03-01T00:00:00");

        Project scanningProject = project("pending-2", "Scanning", ProjectStatus.PENDING);
        scanningProject.setUpdatedAt("2024-01-01T00:00:00");
        scanningProject.setVersions(List.of(version("2.0.0", scanResult(ScanStatus.SCANNING))));

        Project pendingReviewProject = project("published-1", "Review Me", ProjectStatus.PUBLISHED);
        pendingReviewProject.setUpdatedAt("2024-02-01T00:00:00");
        ProjectVersion reviewVersion = version("3.0.0", null);
        reviewVersion.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        pendingReviewProject.setVersions(List.of(reviewVersion));

        when(mongoTemplate.find(any(Query.class), eq(Project.class)))
                .thenReturn(List.of(pendingProject, scanningProject))
                .thenReturn(List.of(pendingReviewProject, pendingProject));

        List<Project> queue = projectReviewQueueService.getVerificationQueue();

        assertEquals(List.of("published-1", "pending-1"), queue.stream().map(Project::getId).toList());
    }

    private static Project project(String id, String title, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setTitle(title);
        project.setStatus(status);
        return project;
    }

    private static ProjectVersion version(String versionNumber, ScanResult scanResult) {
        ProjectVersion version = new ProjectVersion();
        version.setVersionNumber(versionNumber);
        version.setScanResult(scanResult);
        return version;
    }

    private static ScanResult scanResult(ScanStatus status) {
        ScanResult scanResult = new ScanResult();
        scanResult.setStatus(status);
        return scanResult;
    }
}
