package net.modtale.service.project.lifecycle;

import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.admin.audit.AdminAuditLogger;
import net.modtale.service.admin.project.ProjectModerationService;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.jam.ModjamEmbargoService;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.scan.ScanService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModjamModerationEmbargoTest {
    @Test
    void moderationCannotExposePrivateSecretEntriesAsUnlisted() {
        var projects = mock(ProjectRepository.class);
        var projectService = mock(ProjectService.class);
        var scoring = mock(ScoringService.class);
        var notifications = mock(NotificationService.class);
        var audit = mock(AdminAuditLogger.class);
        var embargo = mock(ModjamEmbargoService.class);
        var service = new ProjectModerationService(projects, projectService,
                mock(ProjectRetentionService.class), mock(ProjectDeletionService.class), scoring,
                notifications, mock(ScanService.class), mock(ProjectVersionAccessService.class), audit, embargo);
        Project project = new Project();
        project.setStatus(ProjectStatus.PRIVATE);
        project.setModjamPublicationPending(true);
        when(projectService.getRawProjectById("secret")).thenReturn(project);
        when(embargo.hasActiveEmbargo(project)).thenReturn(true);
        assertThrows(InvalidProjectRequestException.class, () -> service.unlistProject(new User(), "secret", "moderation"));
        assertEquals(ProjectStatus.PRIVATE, project.getStatus());
        assertTrue(project.isModjamPublicationPending());
        verifyNoInteractions(projects, scoring, notifications, audit);
    }
}
