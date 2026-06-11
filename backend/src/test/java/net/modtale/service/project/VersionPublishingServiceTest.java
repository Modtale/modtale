package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.communication.ProjectNotificationService;
import net.modtale.service.security.SecurityIssueAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionPublishingServiceTest {

    private VersionPublishingService service;
    private MongoTemplate mongoTemplate;
    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private ProjectNotificationService projectNotificationService;
    private SecurityIssueAnalysisService securityIssueAnalysisService;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        projectNotificationService = mock(ProjectNotificationService.class);
        securityIssueAnalysisService = mock(SecurityIssueAnalysisService.class);

        ScheduledReleaseQueryService queryService = new ScheduledReleaseQueryService(mongoTemplate);
        ScheduledReleaseExecutionService executionService = new ScheduledReleaseExecutionService(
                projectRepository,
                projectService,
                projectNotificationService,
                securityIssueAnalysisService
        );
        service = new VersionPublishingService(queryService, executionService);
    }

    @Test
    void processScheduledReleasesPublishesOnlyDueVersions() {
        Project project = new Project();
        project.setId("project-1");
        ProjectVersion dueVersion = version("1.0.0", "2000-01-01T00:00:00");
        ProjectVersion futureVersion = version("2.0.0", "2999-01-01T00:00:00");
        project.setVersions(new ArrayList<>(List.of(dueVersion, futureVersion)));

        when(mongoTemplate.find(any(Query.class), eq(Project.class))).thenReturn(List.of(project));

        service.processScheduledReleases();

        assertEquals(ProjectVersion.ReviewStatus.APPROVED, dueVersion.getReviewStatus());
        assertNull(dueVersion.getScheduledPublishDate());
        assertEquals(ProjectVersion.ReviewStatus.SCHEDULED, futureVersion.getReviewStatus());
        assertEquals("2999-01-01T00:00:00", futureVersion.getScheduledPublishDate());
        assertNotNull(project.getUpdatedAt());

        verify(securityIssueAnalysisService).markIssuesAcceptedForApprovedVersion(dueVersion);
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        verify(projectNotificationService).notifyUpdates(project, "1.0.0");
        verify(projectNotificationService).notifyDependents(project, "1.0.0");
        verify(projectNotificationService, never()).notifyUpdates(project, "2.0.0");
        verify(projectNotificationService, never()).notifyDependents(project, "2.0.0");
    }

    private static ProjectVersion version(String versionNumber, String scheduledPublishDate) {
        ProjectVersion version = new ProjectVersion();
        version.setId("version-" + versionNumber);
        version.setVersionNumber(versionNumber);
        version.setReviewStatus(ProjectVersion.ReviewStatus.SCHEDULED);
        version.setScheduledPublishDate(scheduledPublishDate);
        return version;
    }
}
