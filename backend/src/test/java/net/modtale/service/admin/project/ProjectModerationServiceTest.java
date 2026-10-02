package net.modtale.service.admin.project;

import java.util.ArrayList;
import java.util.List;
import net.modtale.model.project.*;
import net.modtale.model.user.User;
import net.modtale.service.admin.audit.AdminAuditLogger;
import net.modtale.service.admin.review.*;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.lifecycle.*;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.scan.ScanService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectModerationServiceTest {
    private final ProjectReviewPersistence writes = mock(ProjectReviewPersistence.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final ProjectDeletionService deletion = mock(ProjectDeletionService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AdminAuditLogger audit = mock(AdminAuditLogger.class);
    private final ProjectModerationService service = new ProjectModerationService(writes, projects,
            mock(ProjectRetentionService.class), deletion, mock(ScoringService.class), notifications,
            mock(ScanService.class), new ProjectVersionAccessService(null), audit);
    private final User admin = new User();
    private ProjectReviewPersistence.Snapshot setup() {
        var project = new Project(); project.setId("project"); project.setAuthorId("author"); project.setStatus(ProjectStatus.PUBLISHED);
        var version = new ProjectVersion(); version.setId("v1");
        project.setVersions(new ArrayList<>(List.of(version))); admin.setId("admin");
        var snapshot = new ProjectReviewPersistence.Snapshot(new Document(), project);
        when(projects.getRawProjectById("project")).thenReturn(project);
        when(writes.capture(eq("project"), anyString())).thenReturn(snapshot);
        when(projects.getProjectLink(project)).thenReturn("/project/project");
        return snapshot;
    }
    @Test void staleDeleteCannotRemoveFilesOrAnnounceSuccess() {
        setup();
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.deleteProjectVersion(admin, "project", "v1"));
        verifyNoInteractions(deletion, notifications, audit);
        verify(projects, never()).evictProjectCache(any(Project.class));
    }
    @Test void successfulDeletePersistsBeforeCleanup() {
        var snapshot = setup(); var removed = snapshot.project().getVersions().getFirst();
        when(writes.applyVersionList(snapshot)).thenReturn(true);
        service.deleteProjectVersion(admin, "project", "v1");
        assertTrue(snapshot.project().getVersions().isEmpty());
        var order = inOrder(writes, projects, deletion, audit);
        order.verify(writes).applyVersionList(snapshot);
        order.verify(projects).evictProjectCache(snapshot.project());
        order.verify(deletion).deleteVersionFile(removed);
        order.verify(audit).logAction("admin", "DELETE_VERSION", "project", "VERSION", "VerID: v1");
    }
    @Test void staleUnlistCannotNotifyOrEvict() {
        setup();
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.unlistProject(admin, "project", "reason"));
        verifyNoInteractions(notifications, audit);
        verify(projects, never()).evictProjectCache(any(Project.class));
    }
    @Test void successfulUnlistNotifiesAfterPersistence() {
        var snapshot = setup(); when(writes.unlist(snapshot)).thenReturn(true);
        service.unlistProject(admin, "project", "reason");
        var order = inOrder(writes, projects, notifications, audit);
        order.verify(writes).unlist(snapshot);
        order.verify(projects).evictProjectCache(snapshot.project());
        order.verify(notifications).sendNotifcation(anyList(), eq("Project Unlisted"), anyString(), any(), isNull());
        order.verify(audit).logAction("admin", "UNLIST_PROJECT", "project", "PROJECT", "Reason: reason");
    }
}
