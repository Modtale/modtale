package net.modtale.service.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.communication.WebhookService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.storage.StorageService;
import net.modtale.service.user.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ScanService {

    private static final Logger logger = LoggerFactory.getLogger(ScanService.class);

    @Autowired private ProjectRepository projectRepository;
    @Autowired private WardenClientService wardenService;
    @Autowired private StorageService storageService;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ProjectService projectService;
    @Autowired private NotificationService notificationService;
    @Autowired private WebhookService webhookService;
    @Autowired private AccountService accountService;
    @Autowired private AccessControlService accessControlService;
    @Autowired private SecurityIssueAnalysisService securityIssueAnalysisService;

    @Qualifier("taskExecutor")
    @Autowired private Executor taskExecutor;

    @Value("${app.limits.rescans-per-day:5}")
    private int rescanLimitPerDay;

    @Value("${app.security.auto-approve-delay-minutes-min:2}")
    private long autoApproveDelayMinutesMin;

    @Value("${app.security.auto-approve-delay-minutes-max:12}")
    private long autoApproveDelayMinutesMax;

    @Value("${app.security.known-risk-delay-minutes-min:15}")
    private long knownRiskDelayMinutesMin;

    @Value("${app.security.known-risk-delay-minutes-max:120}")
    private long knownRiskDelayMinutesMax;

    @Value("${app.security.scan-timeout-minutes:25}")
    private long scanTimeoutMinutes;

    @Value("${app.security.scan-max-retries:2}")
    private int scanMaxRetries;

    private final Map<String, Bucket> rescanBuckets = new ConcurrentHashMap<>();

    public void triggerRescan(String projectId, String versionId) {
        User user = accountService.getCurrentUser();
        if (user != null && !accessControlService.isAdmin(user)) {
            Bucket bucket = rescanBuckets.computeIfAbsent(user.getId(),
                    key -> Bucket.builder()
                            .addLimit(Bandwidth.classic(rescanLimitPerDay, Refill.greedy(rescanLimitPerDay, Duration.ofDays(1))))
                            .build());
            if (!bucket.tryConsume(1)) {
                throw new IllegalStateException("Daily rescan limit reached. Please wait 24 hours.");
            }
        }

        Project project = projectService.getRawProjectById(projectId);
        if (project == null) throw new IllegalArgumentException("Project not found");

        ProjectVersion version = findVersion(project, versionId);
        if (version == null) throw new IllegalArgumentException("Version not found");
        if (version.getFileUrl() == null) throw new IllegalArgumentException("Version has no file to scan");

        int attempt = nextScanAttempt(version.getScanResult());

        ScanResult pending = createQueuedScanResult(attempt, "Manual rescan requested.");
        version.setScanResult(pending);
        version.setReviewStatus(ProjectVersion.ReviewStatus.PENDING);
        version.setScheduledPublishDate(null);

        projectRepository.save(project);
        projectService.evictProjectCache(project);

        String originalFilename = extractOriginalFilename(version.getFileUrl());
        logger.info("Queued manual scan retry for project={} version={} attempt={}", projectId, versionId, attempt);

        enqueueBackgroundScan(projectId, versionId, version.getFileUrl(), originalFilename, true, attempt);
    }

    public ScanResult createQueuedScanResult(int attempt, String note) {
        ScanResult pending = new ScanResult();
        pending.setStatus(ScanStatus.SCANNING);
        pending.setScanState("QUEUED");
        pending.setRiskScore(0);
        pending.setConfidenceScore(0);
        pending.setScanAttempt(Math.max(1, attempt));
        pending.setScanTimestamp(System.currentTimeMillis());

        List<String> notes = new ArrayList<>();
        if (note != null && !note.isBlank()) {
            notes.add(note);
        }
        pending.setReviewerNotes(notes);
        return pending;
    }

    public int nextScanAttempt(ScanResult existing) {
        if (existing == null || existing.getScanAttempt() <= 0) {
            return 1;
        }
        return existing.getScanAttempt() + 1;
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

    private void processBackgroundScan(
            String projectId,
            String versionId,
            String filePath,
            String originalFilename,
            boolean isManualRescan,
            int expectedAttempt
    ) {
        if (!markAttemptRunning(projectId, versionId, expectedAttempt)) {
            logger.info("Scan attempt skipped because state moved ahead project={} version={} attempt={}", projectId, versionId, expectedAttempt);
            return;
        }

        logger.info("Starting scan project={} version={} attempt={} manualRescan={}", projectId, versionId, expectedAttempt, isManualRescan);

        try {
            byte[] fileBytes = storageService.download(filePath);
            ScanResult scanResult = wardenService.scanFile(fileBytes, originalFilename);
            securityIssueAnalysisService.normalizeScanResult(scanResult);
            scanResult.setScanAttempt(expectedAttempt);

            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) {
                logger.warn("Scan completed but project no longer exists project={} version={} attempt={}", projectId, versionId, expectedAttempt);
                return;
            }

            ProjectVersion targetVersion = findVersion(project, versionId);
            if (targetVersion == null) {
                logger.warn("Scan completed but version no longer exists project={} version={} attempt={}", projectId, versionId, expectedAttempt);
                return;
            }

            SecurityIssueAnalysisService.BaselineIndex baselines =
                    securityIssueAnalysisService.collectApprovedIssueBaselines(project, versionId);

            SecurityIssueAnalysisService.ClassificationStats classification =
                    securityIssueAnalysisService.annotateAgainstBaselines(scanResult, baselines);

            RoutingDecision routingDecision = decideRouting(scanResult, classification, isManualRescan);

            Update update = new Update()
                    .set("versions.$.scanResult", scanResult)
                    .set("updatedAt", LocalDateTime.now().toString());

            boolean approvedImmediately = false;
            boolean notifyFlagged = false;

            if (routingDecision.action() == RoutingAction.APPROVE_NOW) {
                update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.APPROVED);
                update.set("versions.$.scheduledPublishDate", null);
                approvedImmediately = true;
            } else if (routingDecision.action() == RoutingAction.SCHEDULE) {
                update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.SCHEDULED);
                update.set("versions.$.scheduledPublishDate", LocalDateTime.now().plusMinutes(routingDecision.delayMinutes()).toString());
            } else {
                update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING);
                update.set("versions.$.scheduledPublishDate", null);
                notifyFlagged = true;
            }

            long modified = mongoTemplate.updateFirst(buildVersionAttemptQuery(projectId, versionId, expectedAttempt), update, Project.class)
                    .getModifiedCount();

            if (modified == 0) {
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
                ProjectVersion refreshedVersion = findVersion(refreshed, versionId);
                webhookService.triggerAdminFlaggedVersionWebhook(refreshed, refreshedVersion, scanResult);
            }

            notifyProjectSubmissionIfReady(projectId, versionId);

            if (approvedImmediately && refreshed != null && refreshed.getStatus() == ProjectStatus.PUBLISHED) {
                ProjectVersion approvedVersion = findVersion(refreshed, versionId);
                if (approvedVersion != null) {
                    securityIssueAnalysisService.markIssuesAcceptedForApprovedVersion(approvedVersion);
                    projectRepository.save(refreshed);
                    projectService.evictProjectCache(refreshed);
                    notificationService.notifyUpdates(refreshed, approvedVersion.getVersionNumber());
                    notificationService.notifyDependents(refreshed, approvedVersion.getVersionNumber());
                }
            }

        } catch (Exception e) {
            ScanResult degraded = buildPipelineErrorResult(e, originalFilename, expectedAttempt);
            boolean applied = updateFailedScan(projectId, versionId, degraded, expectedAttempt);

            if (applied) {
                Project project = projectRepository.findById(projectId).orElse(null);
                if (project != null) {
                    ProjectVersion ver = findVersion(project, versionId);
                    webhookService.triggerAdminFlaggedVersionWebhook(project, ver, degraded);
                }
                notifyProjectSubmissionIfReady(projectId, versionId);
            }

            logger.error("Scan pipeline failed project={} version={} attempt={} message={}", projectId, versionId, expectedAttempt, e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${app.security.scan-recovery-check-ms:120000}")
    public void recoverStaleScanningVersions() {
        long timeoutMs = Math.max(1, scanTimeoutMinutes) * 60_000L;
        long now = System.currentTimeMillis();

        Query staleQuery = new Query(Criteria.where("versions").elemMatch(
                Criteria.where("scanResult.status").is(ScanStatus.SCANNING.name())
        ));

        List<Project> projects = mongoTemplate.find(staleQuery, Project.class);
        for (Project project : projects) {
            if (project == null || project.getVersions() == null) continue;

            for (ProjectVersion version : project.getVersions()) {
                if (version == null) continue;
                ScanResult scanResult = version.getScanResult();
                if (scanResult == null || scanResult.getStatus() != ScanStatus.SCANNING) continue;

                long startedAt = scanResult.getScanTimestamp();
                boolean stale = startedAt <= 0 || (now - startedAt) > timeoutMs;
                if (!stale) continue;

                int currentAttempt = Math.max(1, scanResult.getScanAttempt());
                if (version.getFileUrl() != null && currentAttempt <= Math.max(1, scanMaxRetries)) {
                    int nextAttempt = currentAttempt + 1;
                    ScanResult queued = createQueuedScanResult(
                            nextAttempt,
                            "Previous scan attempt timed out and was re-queued automatically."
                    );

                    Update retryUpdate = new Update()
                            .set("versions.$.scanResult", queued)
                            .set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                            .set("versions.$.scheduledPublishDate", null)
                            .set("updatedAt", LocalDateTime.now().toString());

                    long modified = mongoTemplate.updateFirst(
                            buildVersionAttemptQuery(project.getId(), version.getId(), currentAttempt),
                            retryUpdate,
                            Project.class
                    ).getModifiedCount();

                    if (modified > 0) {
                        logger.warn(
                                "Recovered stale scan by retrying project={} version={} previousAttempt={} nextAttempt={}",
                                project.getId(),
                                version.getId(),
                                currentAttempt,
                                nextAttempt
                        );

                        enqueueBackgroundScan(
                                project.getId(),
                                version.getId(),
                                version.getFileUrl(),
                                extractOriginalFilename(version.getFileUrl()),
                                false,
                                nextAttempt
                        );
                    }
                } else {
                    ScanResult timedOut = buildScanTimeoutResult(
                            extractOriginalFilename(version.getFileUrl()),
                            currentAttempt
                    );
                    boolean applied = updateFailedScan(project.getId(), version.getId(), timedOut, currentAttempt);
                    if (applied) {
                        logger.error(
                                "Scan timed out permanently project={} version={} attempt={}",
                                project.getId(),
                                version.getId(),
                                currentAttempt
                        );

                        Project refreshed = projectRepository.findById(project.getId()).orElse(null);
                        if (refreshed != null) {
                            ProjectVersion refreshedVersion = findVersion(refreshed, version.getId());
                            webhookService.triggerAdminFlaggedVersionWebhook(refreshed, refreshedVersion, timedOut);
                        }
                        notifyProjectSubmissionIfReady(project.getId(), version.getId());
                    }
                }
            }

            projectService.evictProjectCache(project);
        }
    }

    private RoutingDecision decideRouting(
            ScanResult scanResult,
            SecurityIssueAnalysisService.ClassificationStats classification,
            boolean isManualRescan
    ) {
        String verdict = scanResult.getVerdict() == null ? "" : scanResult.getVerdict().toUpperCase();
        ScanStatus status = scanResult.getStatus();

        boolean block = "BLOCK".equals(verdict) || status == ScanStatus.INFECTED;
        boolean explicitReview = "REVIEW".equals(verdict)
                || status == ScanStatus.SUSPICIOUS
                || status == ScanStatus.FLAGGED
                || "UPSTREAM_UNAVAILABLE".equalsIgnoreCase(scanResult.getScanState())
                || hasRecoverableScannerErrors(scanResult);

        boolean knownOnly = classification.knownOnly();
        boolean hasEscalation = classification.escalatedIssueCount() > 0;

        if (isManualRescan) {
            if (block || (explicitReview && (!knownOnly || hasEscalation))) {
                return new RoutingDecision(RoutingAction.REQUIRE_REVIEW, 0);
            }
            return new RoutingDecision(RoutingAction.APPROVE_NOW, 0);
        }

        if (block) {
            return new RoutingDecision(RoutingAction.REQUIRE_REVIEW, 0);
        }

        if (explicitReview) {
            if (knownOnly && !hasEscalation && !"UPSTREAM_UNAVAILABLE".equalsIgnoreCase(scanResult.getScanState())) {
                return new RoutingDecision(
                        RoutingAction.SCHEDULE,
                        randomDelay(knownRiskDelayMinutesMin, knownRiskDelayMinutesMax)
                );
            }
            return new RoutingDecision(RoutingAction.REQUIRE_REVIEW, 0);
        }

        long holdDelayMinutes = holdDelayFromWarden(scanResult);
        long randomizedDelay = randomDelay(autoApproveDelayMinutesMin, autoApproveDelayMinutesMax);
        return new RoutingDecision(RoutingAction.SCHEDULE, Math.max(holdDelayMinutes, randomizedDelay));
    }

    private boolean markAttemptRunning(String projectId, String versionId, int attempt) {
        Update update = new Update()
                .set("versions.$.scanResult.status", ScanStatus.SCANNING)
                .set("versions.$.scanResult.scanState", "SCANNING")
                .set("versions.$.scanResult.scanTimestamp", System.currentTimeMillis())
                .set("versions.$.scanResult.scanAttempt", attempt)
                .set("versions.$.scheduledPublishDate", null)
                .set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                .set("updatedAt", LocalDateTime.now().toString());

        long modified = mongoTemplate.updateFirst(buildVersionAttemptQuery(projectId, versionId, attempt), update, Project.class)
                .getModifiedCount();

        return modified > 0;
    }

    private long holdDelayFromWarden(ScanResult scanResult) {
        long holdUntil = scanResult.getHoldUntilTimestamp();
        if (holdUntil <= 0) return 0;
        long remainingMs = holdUntil - System.currentTimeMillis();
        if (remainingMs <= 0) return 0;
        return (long) Math.ceil(remainingMs / 60000.0);
    }

    private boolean hasRecoverableScannerErrors(ScanResult scanResult) {
        ScanResult.ScanSummary summary = scanResult.getSummary();
        if (summary == null) return false;
        return summary.getRecoverableErrors() > 0
                || summary.getOversizedEntriesSkipped() > 0
                || summary.getNestedArchiveReadFailures() > 0;
    }

    private long randomDelay(long minMinutes, long maxMinutes) {
        long min = Math.max(1, Math.min(minMinutes, maxMinutes));
        long max = Math.max(min, Math.max(minMinutes, maxMinutes));
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private ScanResult buildPipelineErrorResult(Exception e, String filename, int attempt) {
        ScanResult failed = new ScanResult();
        failed.setStatus(ScanStatus.SUSPICIOUS);
        failed.setVerdict("REVIEW");
        failed.setRiskLevel("HIGH");
        failed.setScanState("PIPELINE_ERROR");
        failed.setRiskScore(50);
        failed.setConfidenceScore(30);
        failed.setScanTimestamp(System.currentTimeMillis());
        failed.setScanAttempt(Math.max(1, attempt));

        ScanResult.ScanIssue issue = new ScanResult.ScanIssue();
        issue.setSeverity("MEDIUM");
        issue.setType("ScanPipelineError");
        issue.setCategory("System");
        issue.setDescription("Scan pipeline failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        issue.setFilePath(filename == null ? "uploaded-artifact" : filename);
        issue.setLineStart(-1);
        issue.setLineEnd(-1);
        issue.setScoreImpact(6);
        issue.setConfidence(35);
        issue.setReviewPriority("P1");

        failed.setIssues(List.of(issue));
        failed.setReviewerNotes(List.of(
                "Scan pipeline encountered an internal error.",
                "Manual review is required before publishing."
        ));

        return failed;
    }

    private ScanResult buildScanTimeoutResult(String filename, int attempt) {
        ScanResult timedOut = new ScanResult();
        timedOut.setStatus(ScanStatus.SUSPICIOUS);
        timedOut.setVerdict("REVIEW");
        timedOut.setRiskLevel("HIGH");
        timedOut.setScanState("SCAN_TIMEOUT");
        timedOut.setRiskScore(52);
        timedOut.setConfidenceScore(28);
        timedOut.setScanTimestamp(System.currentTimeMillis());
        timedOut.setScanAttempt(Math.max(1, attempt));

        ScanResult.ScanIssue issue = new ScanResult.ScanIssue();
        issue.setSeverity("MEDIUM");
        issue.setType("ScanTimeout");
        issue.setCategory("System");
        issue.setDescription("Scan exceeded timeout window and could not be completed automatically.");
        issue.setFilePath(filename == null ? "uploaded-artifact" : filename);
        issue.setLineStart(-1);
        issue.setLineEnd(-1);
        issue.setScoreImpact(7);
        issue.setConfidence(40);
        issue.setReviewPriority("P1");

        timedOut.setIssues(List.of(issue));
        timedOut.setReviewerNotes(List.of(
                "The scan timed out after repeated attempts.",
                "Manual review is required before publishing."
        ));

        return timedOut;
    }

    private boolean updateFailedScan(String projectId, String versionId, ScanResult failed, int expectedAttempt) {
        Update update = new Update()
                .set("versions.$.scanResult", failed)
                .set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                .set("versions.$.scheduledPublishDate", null)
                .set("updatedAt", LocalDateTime.now().toString());

        long modified = mongoTemplate.updateFirst(buildVersionAttemptQuery(projectId, versionId, expectedAttempt), update, Project.class)
                .getModifiedCount();

        Project project = projectRepository.findById(projectId).orElse(null);
        projectService.evictProjectCache(project);

        return modified > 0;
    }

    private Query buildVersionAttemptQuery(String projectId, String versionId, int attempt) {
        Criteria attemptCriteria;
        if (attempt <= 1) {
            attemptCriteria = new Criteria().orOperator(
                    Criteria.where("scanResult.scanAttempt").is(1),
                    Criteria.where("scanResult.scanAttempt").is(0),
                    Criteria.where("scanResult.scanAttempt").exists(false)
            );
        } else {
            attemptCriteria = Criteria.where("scanResult.scanAttempt").is(attempt);
        }

        return new Query(
                Criteria.where("_id").is(projectId)
                        .and("versions").elemMatch(
                                Criteria.where("_id").is(versionId)
                                        .andOperator(attemptCriteria)
                        )
        );
    }

    private void notifyProjectSubmissionIfReady(String projectId, String versionId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || project.getStatus() != ProjectStatus.PENDING) return;

        boolean stillScanning = project.getVersions().stream()
                .anyMatch(v -> !v.getId().equals(versionId)
                        && v.getScanResult() != null
                        && v.getScanResult().getStatus() == ScanStatus.SCANNING);

        if (!stillScanning) {
            webhookService.triggerAdminNewProjectWebhook(project);
        }
    }

    private ProjectVersion findVersion(Project project, String versionId) {
        if (project == null || project.getVersions() == null) return null;
        return project.getVersions().stream()
                .filter(v -> v.getId().equals(versionId))
                .findFirst()
                .orElse(null);
    }

    private String extractOriginalFilename(String path) {
        if (path == null || path.isBlank()) return "uploaded-artifact";
        String originalFilename = path.substring(path.lastIndexOf('/') + 1);
        if (originalFilename.length() > 37 && originalFilename.charAt(36) == '-') {
            return originalFilename.substring(37);
        }
        return originalFilename;
    }

    private enum RoutingAction {
        REQUIRE_REVIEW,
        SCHEDULE,
        APPROVE_NOW
    }

    private record RoutingDecision(RoutingAction action, long delayMinutes) {}
}
