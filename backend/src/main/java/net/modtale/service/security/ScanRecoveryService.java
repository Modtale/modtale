package net.modtale.service.security;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.service.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScanRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(ScanRecoveryService.class);

    private final ProjectService projectService;
    private final ScanRoutingService scanRoutingService;
    private final ScanPersistenceService scanPersistenceService;
    private final ScanCompletionService scanCompletionService;

    public ScanRecoveryService(
            ProjectService projectService,
            ScanRoutingService scanRoutingService,
            ScanPersistenceService scanPersistenceService,
            ScanCompletionService scanCompletionService
    ) {
        this.projectService = projectService;
        this.scanRoutingService = scanRoutingService;
        this.scanPersistenceService = scanPersistenceService;
        this.scanCompletionService = scanCompletionService;
    }

    public void recoverStaleScanningVersions(BackgroundScanScheduler backgroundScanScheduler) {
        long timeoutMs = scanRoutingService.scanTimeoutMillis();
        long now = System.currentTimeMillis();

        List<Project> projects = scanPersistenceService.findProjectsWithScanningVersions();
        for (Project project : projects) {
            if (project == null || project.getVersions() == null) {
                continue;
            }

            for (ProjectVersion version : project.getVersions()) {
                if (version == null) {
                    continue;
                }

                ScanResult scanResult = version.getScanResult();
                if (scanResult == null || scanResult.getStatus() != ScanStatus.SCANNING) {
                    continue;
                }

                long startedAt = scanResult.getScanTimestamp();
                boolean stale = startedAt <= 0 || (now - startedAt) > timeoutMs;
                if (!stale) {
                    continue;
                }

                int currentAttempt = Math.max(1, scanResult.getScanAttempt());
                if (version.getFileUrl() != null && currentAttempt <= Math.max(1, scanRoutingService.scanMaxRetries())) {
                    int nextAttempt = currentAttempt + 1;
                    ScanResult queued = scanRoutingService.createQueuedScanResult(
                            nextAttempt,
                            "Previous scan attempt timed out and was re-queued automatically."
                    );

                    if (scanPersistenceService.queueRetryAttempt(project.getId(), version.getId(), currentAttempt, queued)) {
                        logger.warn(
                                "Recovered stale scan by retrying project={} version={} previousAttempt={} nextAttempt={}",
                                project.getId(),
                                version.getId(),
                                currentAttempt,
                                nextAttempt
                        );

                        backgroundScanScheduler.enqueue(
                                project.getId(),
                                version.getId(),
                                version.getFileUrl(),
                                extractOriginalFilename(version.getFileUrl()),
                                false,
                                nextAttempt
                        );
                    }
                } else {
                    scanCompletionService.handleTimedOutScan(
                            project.getId(),
                            version.getId(),
                            extractOriginalFilename(version.getFileUrl()),
                            currentAttempt
                    );
                }
            }

            projectService.evictProjectCache(project);
        }
    }

    private String extractOriginalFilename(String path) {
        if (path == null || path.isBlank()) {
            return "uploaded-artifact";
        }
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == path.length() - 1) {
            return path;
        }
        return path.substring(slashIndex + 1);
    }

    @FunctionalInterface
    public interface BackgroundScanScheduler {
        void enqueue(
                String projectId,
                String versionId,
                String filePath,
                String originalFilename,
                boolean isManualRescan,
                int expectedAttempt
        );
    }
}
