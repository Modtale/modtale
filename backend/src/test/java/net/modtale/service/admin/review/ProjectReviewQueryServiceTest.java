package net.modtale.service.admin.review;

import java.util.List;
import java.util.Optional;
import org.bson.Document;
import net.modtale.model.user.ApiKey;
import net.modtale.model.dto.admin.AdminVerificationQueueItemDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectReviewQueryServiceTest {

    @Test
    void verificationQueueReturnsRiskOrderedDedicatedDtos() {
        ProjectReviewQueueService queueService = mock(ProjectReviewQueueService.class);
        ProjectReviewQueryService queryService = new ProjectReviewQueryService(
                mock(UserRepository.class),
                mock(ProjectService.class),
                queueService,
                mock(MongoTemplate.class)
        );

        Project olderClean = queueProject("clean", "2026-01-01T00:00:00", ScanStatus.CLEAN, "ALLOW", 5, 0);
        Project newerRisky = queueProject("risky", "2026-02-01T00:00:00", ScanStatus.SUSPICIOUS, "REVIEW", 80, 2);
        when(queueService.getVerificationQueue()).thenReturn(List.of(olderClean, newerRisky));

        List<AdminVerificationQueueItemDTO> queue = queryService.getVerificationQueue();

        assertEquals(List.of("risky", "clean"), queue.stream().map(AdminVerificationQueueItemDTO::id).toList());
        assertEquals(80, queue.getFirst().pendingVersion().scan().riskScore());
        assertEquals(2, queue.getFirst().pendingVersion().scan().newIssueCount());
    }

    @Test
    void reviewDetailsCountsProjectsWithoutLoadingTheirDocuments() {
        UserRepository users = mock(UserRepository.class);
        ProjectService projects = mock(ProjectService.class);
        MongoTemplate mongo = mock(MongoTemplate.class);
        ProjectReviewQueryService queryService = new ProjectReviewQueryService(users, projects,
                mock(ProjectReviewQueueService.class), mongo);
        Project project = new Project();
        project.setId("project-1");
        project.setAuthorId("author-1");
        User author = mock(User.class);
        when(author.getId()).thenReturn("author-1");
        when(author.getTier()).thenReturn(ApiKey.Tier.USER);
        when(projects.getRawProjectById("project-1")).thenReturn(project);
        when(mongo.aggregate(any(Aggregation.class), eq("projects"), eq(Project.class)))
                .thenReturn(new AggregationResults<>(List.of(project), new Document()));
        when(users.findById("author-1")).thenReturn(Optional.of(author));
        when(mongo.count(any(Query.class), org.mockito.Mockito.eq(Project.class))).thenReturn(42L);

        assertEquals(42L, queryService.getProjectReviewDetails("project-1").authorStats().totalProjects());
        verify(mongo).count(any(Query.class), org.mockito.Mockito.eq(Project.class));
    }

    private static Project queueProject(
            String id,
            String updatedAt,
            ScanStatus scanStatus,
            String verdict,
            int riskScore,
            int newIssueCount
    ) {
        ScanResult scan = new ScanResult();
        scan.setStatus(scanStatus);
        scan.setVerdict(verdict);
        scan.setRiskScore(riskScore);
        scan.setNewIssueCount(newIssueCount);

        ProjectVersion version = new ProjectVersion();
        version.setId(id + "-version");
        version.setVersionNumber("1.0.0");
        version.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        version.setScanResult(scan);

        Project project = new Project();
        project.setId(id);
        project.setTitle(id);
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setUpdatedAt(updatedAt);
        project.setVersions(List.of(version));
        return project;
    }
}
