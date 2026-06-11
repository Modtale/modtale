package net.modtale.service.security;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.communication.ProjectNotificationService;
import net.modtale.service.communication.WebhookService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.project.ProjectVersionAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScanCompletionService {

    private static final Logger logger = LoggerFactory.getLogger(ScanCompletionService.class);

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final ProjectNotificationService projectNotificationService;
    private final WebhookService webhookService;
    private final SecurityIssueAnalysisService securityIssueAnalysisService;
    private final ScanRoutingService scanRoutingService;
    private final ScanPersistenceService scanPersistenceService;
    private final ProjectVersionAccessService projectVersionAccessService;

    public ScanCompletionService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            ProjectNotificationService projectNotificationService,
            WebhookService webhookService,
            SecurityIssueAnalysisService securityIssueAnalysisService,
            ScanRoutingService scanRoutingService,
            ScanPersistenceService scanPersistenceService,
            ProjectVersionAccessService projectVersionAccessService
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.projectNotificationService = projectNotificationService;
        this.webhookService = webhookService;
        this.securityIssueAnalysisService = securityIssueAnalysisService;
        this.scanRoutingService = scanRoutingService;
        this.scanPersistenceService = scanPersistenceService;
        this.projectVersionAccessService = projectVersionAccessService;
    }

    public void handleCompletedScan(
            String projectId,
            String versionId,
            int expectedAttempt,
            boolean isManualRescan,
            ScanResult scanResult
    ) {
        securityIssueAnalysisService.normalizeScanResult(scanResult);
        scanResult.setScanAttempt(expectedAttempt);

        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            logger.warn("Scan completed but project no longer exists project={} version={} attempt={}", projectId, versionId, expectedAttempt);
            return;
        }

        ProjectVersion targetVersion = projectVersionAccessService.findById(project, versionId);
        if (targetVersion == null) {
            logger.warn("Scan completed but version no longer exists project={} version={} attempt={}", projectId, versionId, expectedAttempt);
            return;
        }

        SecurityIssueAnalysisService.BaselineIndex baselines =
                securityIssueAnalysisService.collectApprovedIssueBaselines(project, versionId);
        SecurityIssueAnalysisService.ClassificationStats classification =
                securityIssueAnalysisService.annotateAgainstBaselines(scanResult, baselines);
        ScanRoutingService.RoutingDecision routingDecision =
                scanRoutingService.decideRouting(scanResult, classification, isManualRescan);

        boolean approvedImmediately = routingDecision.action() == ScanRoutingService.RoutingAction.APPROVE_NOW;
        boolean notifyFlagged = routingDecision.action() == ScanRoutingService.RoutingAction.REQUIRE_REVIEW;

        if (!scanPersistenceService.applyScanOutcome(projectId, versionId, expectedAttempt, scanResult, routingDecision)) {
            logger.info("Scan result ignored because a newer attempt already exists project={} version={} attempt={}", projectId, versionId, expectedAttempt);
            return;
        }

        Project refreshed = projectRepository.findById(projectId).orElse(null);
        projectService.evictProjectCache(refreshed != null ? refreshed : project);

        logger.info(
                "Completed scan project={} version={} attempt={} verdict={} status={} known={} new={} escalated={} route={}",
                projectId,
                versionId,
                expectedAttempt,
                scanResult.getVerdict(),
                scanResult.getStatus(),
                classification.knownIssueCount(),
                classification.newIssueCount(),
                classification.escalatedIssueCount(),
                routingDecision.action()
        );

        if (refreshed != null && notifyFlagged) {
            ProjectVersion refreshedVersion = projectVersionAccessService.findById(refreshed, versionId);
            webhookService.triggerAdminFlaggedVersionWebhook(refreshed, refreshedVersion, scanResult);
        }

        notifyProjectSubmissionIfReady(projectId, versionId);

        if (approvedImmediately && refreshed != null && refreshed.getStatus() == ProjectStatus.PUBLISHED) {
            ProjectVersion approvedVersion = projectVersionAccessService.findById(refreshed, versionId);
            if (approvedVersion != null) {
                securityIssueAnalysisService.markIssuesAcceptedForApprovedVersion(approvedVersion);
                projectRepository.save(refreshed);
                projectService.evictProjectCache(refreshed);
                projectNotificationService.notifyUpdates(refreshed, approvedVersion.getVersionNumber());
                projectNotificationService.notifyDependents(refreshed, approvedVersion.getVersionNumber());
            }
        }
    }

    public void handleScanFailure(
            String projectId,
            String versionId,
            String originalFilename,
            int expectedAttempt,
            RuntimeException exception
    ) {
        ScanResult degraded = scanRoutingService.buildPipelineErrorResult(exception, originalFilename, expectedAttempt);
        boolean applied = scanPersistenceService.updateFailedScan(projectId, versionId, degraded, expectedAttempt);

        if (applied) {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project != null) {
                ProjectVersion version = projectVersionAccessService.findById(project, versionId);
                webhookService.triggerAdminFlaggedVersionWebhook(project, version, degraded);
            }
            notifyProjectSubmissionIfReady(projectId, versionId);
        }

        logger.error("Scan pipeline failed project={} version={} attempt={} message={}", projectId, versionId, expectedAttempt, exception.getMessage(), exception);
    }

    public void handleTimedOutScan(
            String projectId,
            String versionId,
            String originalFilename,
            int expectedAttempt
    ) {
        ScanResult timedOut = scanRoutingService.buildScanTimeoutResult(originalFilename, expectedAttempt);
        boolean applied = scanPersistenceService.updateFailedScan(projectId, versionId, timedOut, expectedAttempt);
        if (!applied) {
            return;
        }

        logger.error("Scan timed out permanently project={} version={} attempt={}", projectId, versionId, expectedAttempt);

        Project refreshed = projectRepository.findById(projectId).orElse(null);
        if (refreshed != null) {
            ProjectVersion refreshedVersion = projectVersionAccessService.findById(refreshed, versionId);
            webhookService.triggerAdminFlaggedVersionWebhook(refreshed, refreshedVersion, timedOut);
        }
        notifyProjectSubmissionIfReady(projectId, versionId);
    }

    private void notifyProjectSubmissionIfReady(String projectId, String versionId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || project.getStatus() != ProjectStatus.PENDING) {
            return;
        }

        boolean stillScanning = project.getVersions().stream()
                .anyMatch(version -> !version.getId().equals(versionId)
                        && version.getScanResult() != null
                        && version.getScanResult().getStatus() == ScanStatus.SCANNING);

        if (!stillScanning) {
            webhookService.triggerAdminNewProjectWebhook(project);
        }
    }
}
