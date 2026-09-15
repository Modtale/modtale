package net.modtale.service.security.scan;

import java.util.List;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.VersionNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScanRequestServiceTest {

    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private ScanThrottleService scanThrottleService;
    private ScanRoutingService scanRoutingService;
    private ProjectVersionAccessService projectVersionAccessService;
    private ScanExecutionService scanExecutionService;
    private ScanRequestService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        scanThrottleService = mock(ScanThrottleService.class);
        scanRoutingService = mock(ScanRoutingService.class);
        projectVersionAccessService = mock(ProjectVersionAccessService.class);
        scanExecutionService = mock(ScanExecutionService.class);
        service = new ScanRequestService(
                projectRepository,
                projectService,
                scanThrottleService,
                scanRoutingService,
                projectVersionAccessService,
                scanExecutionService
        );
    }

    @Test
    void triggerRescanQueuesPendingScanAndEnqueuesBackgroundWork() {
        User user = new User();
        user.setId("user-1");
        Project project = new Project();
        project.setId("project-1");
        ProjectVersion version = new ProjectVersion();
        version.setId("version-1");
        version.setFileUrl("files/mod.jar");
        version.setScheduledPublishDate("2026-01-01T00:00:00");
        version.setReviewStatus(ProjectVersion.ReviewStatus.SCHEDULED);
        project.setVersions(List.of(version));
        ScanResult queued = new ScanResult();
        queued.setStatus(ScanStatus.SCANNING);

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(projectVersionAccessService.findById(project, "version-1")).thenReturn(version);
        when(scanRoutingService.nextScanAttempt(version.getScanResult())).thenReturn(2);
        when(scanRoutingService.createQueuedScanResult(2, "Manual rescan requested.")).thenReturn(queued);
        when(scanExecutionService.extractOriginalFilename("files/mod.jar")).thenReturn("mod.jar");

        service.triggerRescan("project-1", "version-1", user);

        assertSame(queued, version.getScanResult());
        assertEquals(ProjectVersion.ReviewStatus.PENDING, version.getReviewStatus());
        assertNull(version.getScheduledPublishDate());
        verify(scanThrottleService).enforceRescanLimit(user);
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        verify(scanExecutionService).enqueueBackgroundScan("project-1", "version-1", "files/mod.jar", "mod.jar", true, 2);
    }

    @Test
    void triggerRescanRejectsMissingVersionsOrVersionsWithoutFiles() {
        User user = new User();
        Project project = new Project();
        project.setId("project-1");
        ProjectVersion version = new ProjectVersion();
        version.setId("version-1");

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(projectVersionAccessService.findById(project, "missing")).thenReturn(null);
        when(projectVersionAccessService.findById(project, "version-1")).thenReturn(version);

        assertThrows(VersionNotFoundException.class, () -> service.triggerRescan("project-1", "missing", user));
        assertThrows(InvalidProjectRequestException.class, () -> service.triggerRescan("project-1", "version-1", user));
    }

    @Test
    void createQueuedScanResultAndNextScanAttemptDelegateToRoutingService() {
        ScanResult existing = new ScanResult();
        ScanResult queued = new ScanResult();

        when(scanRoutingService.createQueuedScanResult(3, "note")).thenReturn(queued);
        when(scanRoutingService.nextScanAttempt(existing)).thenReturn(4);

        assertSame(queued, service.createQueuedScanResult(3, "note"));
        assertEquals(4, service.nextScanAttempt(existing));
    }
}
