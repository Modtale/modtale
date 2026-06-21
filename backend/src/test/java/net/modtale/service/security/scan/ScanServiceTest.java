package net.modtale.service.security.scan;

import java.util.List;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScanServiceTest {

    private ScanService service;
    private ScanRequestService scanRequestService;
    private ScanExecutionService scanExecutionService;
    private ProjectRepository projectRepository;
    private ProjectService projectService;
    private ProjectVersionAccessService projectVersionAccessService;
    private ScanThrottleService scanThrottleService;
    private ScanRoutingService scanRoutingService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectService = mock(ProjectService.class);
        projectVersionAccessService = mock(ProjectVersionAccessService.class);
        scanThrottleService = mock(ScanThrottleService.class);
        scanRoutingService = mock(ScanRoutingService.class);
        scanExecutionService = mock(ScanExecutionService.class);
        scanRequestService = new ScanRequestService(
                projectRepository,
                projectService,
                scanThrottleService,
                scanRoutingService,
                projectVersionAccessService,
                scanExecutionService
        );
        service = new ScanService(scanRequestService, scanExecutionService);
    }

    @Test
    void triggerRescanRequeuesTheVersionAndSchedulesAnotherBackgroundAttempt() {
        User user = new User();
        user.setId("user-1");

        ProjectVersion version = new ProjectVersion();
        version.setId("version-1");
        version.setFileUrl("https://cdn.modtale.net/files/generated-mod.jar");
        version.setScanResult(new ScanResult());

        Project project = new Project();
        project.setId("project-1");
        project.setVersions(List.of(version));

        ScanResult pendingScan = new ScanResult();

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(projectVersionAccessService.findById(project, "version-1")).thenReturn(version);
        when(scanRoutingService.nextScanAttempt(version.getScanResult())).thenReturn(2);
        when(scanRoutingService.createQueuedScanResult(2, "Manual rescan requested.")).thenReturn(pendingScan);
        when(scanExecutionService.extractOriginalFilename("https://cdn.modtale.net/files/generated-mod.jar")).thenReturn("generated-mod.jar");

        service.triggerRescan("project-1", "version-1", user);

        assertEquals(pendingScan, version.getScanResult());
        assertEquals(ProjectVersion.ReviewStatus.PENDING, version.getReviewStatus());
        verify(scanThrottleService).enforceRescanLimit(user);
        verify(projectRepository).save(project);
        verify(projectService).evictProjectCache(project);
        ArgumentCaptor<String> originalFilename = ArgumentCaptor.forClass(String.class);
        verify(scanExecutionService).enqueueBackgroundScan(
                eq("project-1"),
                eq("version-1"),
                eq("https://cdn.modtale.net/files/generated-mod.jar"),
                originalFilename.capture(),
                eq(true),
                eq(2)
        );
        assertTrue(originalFilename.getValue().endsWith("generated-mod.jar"));
    }
}
