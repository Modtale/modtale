package net.modtale.service.project.lifecycle;

import net.modtale.model.project.*;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.analytics.*;
import net.modtale.service.communication.*;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectPublicationSecurityTest {
    @Test void restoringVisibilityNeverApprovesPendingOrScheduledVersions() {
        for (ProjectStatus status : List.of(ProjectStatus.ARCHIVED,ProjectStatus.UNLISTED,ProjectStatus.PRIVATE)) {
            var repository=mock(ProjectRepository.class);
            var access=mock(AccessControlService.class);
            var projects=mock(ProjectAccessService.class);
            var analysis=mock(SecurityIssueAnalysisService.class);
            var service=new ProjectPublicationService(repository,mock(ProjectService.class),mock(ProjectNotificationService.class),
                    mock(WebhookService.class),mock(TrackingService.class),mock(ScoringService.class),access,projects,analysis);
            var project=new Project();project.setId("project");project.setStatus(status);project.setCreatedAt("2026-01-01T00:00:00");
            var pending=new ProjectVersion();pending.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
            var scheduled=new ProjectVersion();scheduled.setReviewStatus(ProjectVersion.ReviewStatus.SCHEDULED);
            scheduled.setScheduledPublishDate("2999-01-01T00:00:00");
            project.setVersions(List.of(pending,scheduled));
            var owner=new User();
            when(projects.requireProject("project")).thenReturn(project);
            when(access.hasProjectPermission(project,owner,"PROJECT_STATUS_PUBLISH")).thenReturn(true);
            when(repository.save(project)).thenReturn(project);
            service.publishProject("project",owner);
            assertEquals(ProjectStatus.PUBLISHED,project.getStatus());
            assertEquals(ProjectVersion.ReviewStatus.PENDING,pending.getReviewStatus(),status.name());
            assertEquals(ProjectVersion.ReviewStatus.SCHEDULED,scheduled.getReviewStatus(),status.name());
            assertEquals("2999-01-01T00:00:00",scheduled.getScheduledPublishDate());
            verifyNoInteractions(analysis);
        }
    }
}
