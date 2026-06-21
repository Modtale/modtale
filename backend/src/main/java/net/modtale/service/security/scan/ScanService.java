package net.modtale.service.security.scan;

import net.modtale.model.project.ScanResult;
import net.modtale.model.user.User;
import org.springframework.stereotype.Service;

@Service
public class ScanService {

    private final ScanRequestService scanRequestService;
    private final ScanExecutionService scanExecutionService;

    public ScanService(
            ScanRequestService scanRequestService,
            ScanExecutionService scanExecutionService
    ) {
        this.scanRequestService = scanRequestService;
        this.scanExecutionService = scanExecutionService;
    }

    public void triggerRescan(String projectId, String versionId, User user) {
        scanRequestService.triggerRescan(projectId, versionId, user);
    }

    public ScanResult createQueuedScanResult(int attempt, String note) {
        return scanRequestService.createQueuedScanResult(attempt, note);
    }

    public int nextScanAttempt(ScanResult existing) {
        return scanRequestService.nextScanAttempt(existing);
    }

    public void enqueueBackgroundScan(
            String projectId,
            String versionId,
            String filePath,
            String originalFilename,
            boolean isManualRescan,
            int expectedAttempt
    ) {
        scanExecutionService.enqueueBackgroundScan(
                projectId,
                versionId,
                filePath,
                originalFilename,
                isManualRescan,
                expectedAttempt
        );
    }
}
