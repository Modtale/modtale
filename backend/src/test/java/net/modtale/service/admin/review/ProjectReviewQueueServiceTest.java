package net.modtale.service.admin.review;

import java.util.List;
import org.bson.Document;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    void getVerificationQueueUsesOneFilteredProjection() {
        Project pendingProject = project("pending-1", "Pending", ProjectStatus.PENDING);
        Project pendingReviewProject = project("published-1", "Review Me", ProjectStatus.PUBLISHED);
        ProjectVersion reviewVersion = new ProjectVersion();
        reviewVersion.setVersionNumber("3.0.0");
        reviewVersion.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        pendingReviewProject.setVersions(List.of(reviewVersion));

        when(mongoTemplate.aggregate(any(Aggregation.class), eq("projects"), eq(Project.class)))
                .thenReturn(new AggregationResults<>(List.of(pendingProject, pendingReviewProject), new Document()));

        List<Project> queue = projectReviewQueueService.getVerificationQueue();

        assertEquals(List.of("pending-1", "published-1"), queue.stream().map(Project::getId).toList());

        ArgumentCaptor<Aggregation> aggregationCaptor = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongoTemplate, times(1)).aggregate(aggregationCaptor.capture(), eq("projects"), eq(Project.class));
        String pipeline = aggregationCaptor.getValue().toPipeline(Aggregation.DEFAULT_CONTEXT).toString();
        assertTrue(pipeline.contains("$or"));
        assertTrue(pipeline.contains("$not"));
        assertTrue(pipeline.contains("SCANNING"));
        assertTrue(pipeline.contains("$filter"));
        assertTrue(pipeline.contains("riskScore"));
        assertFalse(pipeline.contains("issues"));
        assertFalse(pipeline.contains("comments"));
    }

    private static Project project(String id, String title, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setTitle(title);
        project.setStatus(status);
        return project;
    }

}
