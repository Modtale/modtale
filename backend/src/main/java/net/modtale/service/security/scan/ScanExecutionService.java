package net.modtale.service.security.scan;

import java.util.concurrent.Executor;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.communication.ProjectNotificationService;
import net.modtale.service.communication.WebhookService;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import net.modtale.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScanExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(ScanExecutionService.class);

    private final WardenClientService wardenService;
    private final StorageService storageService;
    private final Executor taskExecutor;
    private final ScanPersistenceService scanPersistenceService;
    private final ScanCompletionService scanCompletionService;
    private final ScanRecoveryService scanRecoveryService;

    @Autowired
    public ScanExecutionService(
            WardenClientService wardenService,
            StorageService storageService,
            @Qualifier("taskExecutor") Executor taskExecutor,
            ScanPersistenceService scanPersistenceService,
            ScanCompletionService scanCompletionService,
            ScanRecoveryService scanRecoveryService
    ) {
        this.wardenService = wardenService;
        this.storageService = storageService;
        this.taskExecutor = taskExecutor;
        this.scanPersistenceService = scanPersistenceService;
        this.scanCompletionService = scanCompletionService;
        this.scanRecoveryService = scanRecoveryService;
    }

    public ScanExecutionService(
            ProjectRepository projectRepository,
            WardenClientService wardenService,
            StorageService storageService,
            ProjectService projectService,
            ProjectNotificationService projectNotificationService,
            WebhookService webhookService,
            SecurityIssueAnalysisService securityIssueAnalysisService,
            Executor taskExecutor,
            ScanRoutingService scanRoutingService,
            ScanPersistenceService scanPersistenceService,
            ProjectVersionAccessService projectVersionAccessService
    ) {
        ScanCompletionService completionService = new ScanCompletionService(
                projectRepository,
                projectService,
                projectNotificationService,
                webhookService,
                securityIssueAnalysisService,
                scanRoutingService,
                scanPersistenceService,
                projectVersionAccessService
        );
        this.wardenService = wardenService;
        this.storageService = storageService;
        this.taskExecutor = taskExecutor;
        this.scanPersistenceService = scanPersistenceService;
        this.scanCompletionService = completionService;
        this.scanRecoveryService = new ScanRecoveryService(
                projectService,
                scanRoutingService,
                scanPersistenceService,
                completionService
        );
    }

    public void enqueueBackgroundScan(
            String projectId,
            String versionId,
            String filePath,
            String originalFilename,
            boolean isManualRescan,
            int expectedAttempt
    ) {
        taskExecutor.execute(() -> processBackgroundScan(
                projectId,
                versionId,
                filePath,
                originalFilename,
                isManualRescan,
                expectedAttempt
        ));
    }

    @Scheduled(fixedDelayString = "${app.security.scan-recovery-check-ms:120000}")
    public void recoverStaleScanningVersions() {
        scanRecoveryService.recoverStaleScanningVersions(this::enqueueBackgroundScan);
    }

    public String extractOriginalFilename(String path) {
        if (path == null || path.isBlank()) return "uploaded-artifact";
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == path.length() - 1) {
            return path;
        }
        return path.substring(slashIndex + 1);
    }

    private void processBackgroundScan(
            String projectId,
            String versionId,
            String filePath,
            String originalFilename,
            boolean isManualRescan,
            int expectedAttempt
    ) {
        if (!scanPersistenceService.markAttemptRunning(projectId, versionId, expectedAttempt)) {
            logger.info("Scan attempt skipped because state moved ahead project={} version={} attempt={}", projectId, versionId, expectedAttempt);
            return;
        }

        logger.info("Starting scan project={} version={} attempt={} manualRescan={}", projectId, versionId, expectedAttempt, isManualRescan);

        try {
            byte[] fileBytes = storageService.download(filePath);
            scanCompletionService.handleCompletedScan(
                    projectId,
                    versionId,
                    expectedAttempt,
                    isManualRescan,
                    wardenService.scanFile(fileBytes, originalFilename)
            );
        } catch (RuntimeException e) {
            scanCompletionService.handleScanFailure(projectId, versionId, originalFilename, expectedAttempt, e);
        }
    }
}
