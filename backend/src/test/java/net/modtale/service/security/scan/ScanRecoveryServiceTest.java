package net.modtale.service.security.scan;

import java.util.List;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScanRecoveryServiceTest {

    private ProjectService projectService;
    private ScanRoutingService scanRoutingService;
    private ScanPersistenceService scanPersistenceService;
    private ScanCompletionService scanCompletionService;
    private ScanRecoveryService.BackgroundScanScheduler scheduler;
    private ScanRecoveryService service;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        scanRoutingService = mock(ScanRoutingService.class);
        scanPersistenceService = mock(ScanPersistenceService.class);
        scanCompletionService = mock(ScanCompletionService.class);
        scheduler = mock(ScanRecoveryService.BackgroundScanScheduler.class);
        service = new ScanRecoveryService(projectService, scanRoutingService, scanPersistenceService, scanCompletionService);
    }

    @Test
    void recoverStaleScanningVersionsQueuesRetryWhenAttemptsRemain() {
        Project project = project("project-1", version("version-1", "files/mod.jar", 1, staleTimestamp()));
        ScanResult queued = new ScanResult();

        when(scanRoutingService.scanTimeoutMillis()).thenReturn(60_000L);
        when(scanRoutingService.scanMaxRetries()).thenReturn(2);
        when(scanPersistenceService.findProjectsWithScanningVersions()).thenReturn(List.of(project));
        when(scanRoutingService.createQueuedScanResult(2, "Previous scan attempt timed out and was re-queued automatically."))
                .thenReturn(queued);
        when(scanPersistenceService.queueRetryAttempt("project-1", "version-1", 1, queued)).thenReturn(true);

        service.recoverStaleScanningVersions(scheduler);

        verify(scheduler).enqueue("project-1", "version-1", "files/mod.jar", "mod.jar", false, 2);
        verify(scanCompletionService, never()).handleTimedOutScan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(projectService).evictProjectCache(project);
    }

    @Test
    void recoverStaleScanningVersionsMarksTimedOutWhenRetriesAreExhaustedOrFileIsMissing() {
        ProjectVersion exhausted = version("version-1", "files/mod.jar", 3, staleTimestamp());
        ProjectVersion missingFile = version("version-2", null, 1, staleTimestamp());
        Project project = project("project-1", exhausted, missingFile);

        when(scanRoutingService.scanTimeoutMillis()).thenReturn(60_000L);
        when(scanRoutingService.scanMaxRetries()).thenReturn(2);
        when(scanPersistenceService.findProjectsWithScanningVersions()).thenReturn(List.of(project));

        service.recoverStaleScanningVersions(scheduler);

        verify(scanCompletionService).handleTimedOutScan("project-1", "version-1", "mod.jar", 3);
        verify(scanCompletionService).handleTimedOutScan("project-1", "version-2", "uploaded-artifact", 1);
        verify(scheduler, never()).enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void recoverStaleScanningVersionsIgnoresFreshOrNonScanningVersions() {
        ProjectVersion fresh = version("version-1", "files/mod.jar", 1, System.currentTimeMillis());
        ProjectVersion clean = new ProjectVersion();
        clean.setId("version-2");
        clean.setScanResult(new ScanResult());
        clean.getScanResult().setStatus(ScanStatus.CLEAN);
        Project project = project("project-1", fresh, clean);

        when(scanRoutingService.scanTimeoutMillis()).thenReturn(60_000L);
        when(scanPersistenceService.findProjectsWithScanningVersions()).thenReturn(List.of(project));

        service.recoverStaleScanningVersions(scheduler);

        verify(scheduler, never()).enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyInt());
        verify(scanPersistenceService, never()).queueRetryAttempt(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
        verify(scanCompletionService, never()).handleTimedOutScan(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
        verify(projectService).evictProjectCache(project);
    }

    private static Project project(String id, ProjectVersion... versions) {
        Project project = new Project();
        project.setId(id);
        project.setVersions(List.of(versions));
        return project;
    }

    private static ProjectVersion version(String id, String fileUrl, int attempt, long timestamp) {
        ScanResult scanResult = new ScanResult();
        scanResult.setStatus(ScanStatus.SCANNING);
        scanResult.setScanAttempt(attempt);
        scanResult.setScanTimestamp(timestamp);

        ProjectVersion version = new ProjectVersion();
        version.setId(id);
        version.setFileUrl(fileUrl);
        version.setScanResult(scanResult);
        return version;
    }

    private static long staleTimestamp() {
        return System.currentTimeMillis() - 120_000L;
    }
}
