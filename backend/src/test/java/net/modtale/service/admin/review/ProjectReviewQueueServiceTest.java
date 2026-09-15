package net.modtale.service.admin.review;

import java.util.List;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

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

        when(mongoTemplate.find(any(Query.class), eq(Project.class)))
                .thenReturn(List.of(pendingProject, pendingReviewProject));

        List<Project> queue = projectReviewQueueService.getVerificationQueue();

        assertEquals(List.of("pending-1", "published-1"), queue.stream().map(Project::getId).toList());

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, times(1)).find(queryCaptor.capture(), eq(Project.class));
        Query query = queryCaptor.getValue();
        String criteria = query.getQueryObject().toString();
        assertTrue(criteria.contains("$or"));
        assertTrue(criteria.contains("$not"));
        assertTrue(criteria.contains("SCANNING"));
        assertEquals(1, query.getFieldsObject().get("versions.scanResult.riskScore"));
        assertFalse(query.getFieldsObject().containsKey("versions.scanResult.issues"));
        assertFalse(query.getFieldsObject().containsKey("comments"));
    }

    private static Project project(String id, String title, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setTitle(title);
        project.setStatus(status);
        return project;
    }

}
