package net.modtale.service.security.scan;

import java.time.LocalDateTime;
import java.util.List;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.project.query.ProjectService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class ScanPersistenceService {

    private final MongoTemplate mongoTemplate;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;

    public ScanPersistenceService(
            MongoTemplate mongoTemplate,
            ProjectRepository projectRepository,
            ProjectService projectService
    ) {
        this.mongoTemplate = mongoTemplate;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
    }

    public boolean markAttemptRunning(String projectId, String versionId, int attempt) {
        return markAttemptRunning(projectId, versionId, attempt, null);
    }
    public boolean markAttemptRunning(String projectId, String versionId, int attempt, String requestId) {
        Update update = new Update()
                .set("versions.$.scanResult.status", ScanStatus.SCANNING)
                .set("versions.$.scanResult.scanState", "SCANNING")
                .set("versions.$.scanResult.scanTimestamp", System.currentTimeMillis())
                .set("versions.$.scanResult.scanAttempt", attempt)
                .set("versions.$.scheduledPublishDate", null)
                .set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                .set("updatedAt", LocalDateTime.now().toString());

        return mongoTemplate.updateFirst(bindRequest(buildVersionAttemptQuery(projectId, versionId, attempt, "QUEUED"), requestId), update, Project.class)
                .getModifiedCount() > 0;
    }

    public boolean applyScanOutcome(
            String projectId,
            String versionId,
            int expectedAttempt,
            ScanResult scanResult,
            ScanRoutingService.RoutingDecision routingDecision,
            ProjectVersion reviewedVersion
    ) {
        return applyScanOutcome(projectId, versionId, expectedAttempt, scanResult, routingDecision, reviewedVersion, null);
    }
    public boolean applyScanOutcome(String projectId, String versionId, int expectedAttempt, ScanResult scanResult,
            ScanRoutingService.RoutingDecision routingDecision, ProjectVersion reviewedVersion, String requestId) {
        scanResult.setScanRequestId(requestId);
        if (routingDecision.action() == ScanRoutingService.RoutingAction.SCHEDULE
                || routingDecision.action() == ScanRoutingService.RoutingAction.APPROVE_NOW) {
            String context = ArtifactReviewContext.automaticallyReviewableFingerprint(reviewedVersion);
            if (reviewedVersion.getFindingReviewHead() != null || context == null || !context.equals(scanResult.getReviewedContextSha256())
                    || !ArtifactClearancePolicy.cleared(scanResult)) return false;
        }
        if (routingDecision.action() == ScanRoutingService.RoutingAction.DEFER) {
            scanResult.setStatus(ScanStatus.SCANNING);
            scanResult.setScanState("WAITING_RETRY");
            scanResult.setScanTimestamp(System.currentTimeMillis());
            scanResult.setReviewerNotes(List.of("Inspection service capacity was temporarily unavailable. An automatic retry is pending; this version remains unpublished."));
        }
        Update update = new Update()
                .set("versions.$.scanResult", scanResult)
                .set("updatedAt", LocalDateTime.now().toString());

        switch (routingDecision.action()) {
            case APPROVE_NOW -> {
                update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.APPROVED)
                        .set("versions.$.securityApprovalProjectId", reviewedVersion.getSecurityApprovalProjectId())
                        .set("versions.$.approvedReviewOrigins", reviewedVersion.getApprovedReviewOrigins())
                        .set("versions.$.approvedFindingReviewHead", null)
                        .set("versions.$.approvedSecurityEvidence", reviewedVersion.getApprovedSecurityEvidence())
                        .set("versions.$.approvedSecurityContextSha256", reviewedVersion.getApprovedSecurityContextSha256())
                        .set("versions.$.securityApprovedAt", reviewedVersion.getSecurityApprovedAt())
                        .set("versions.$.approvedIssueBaselines", reviewedVersion.getApprovedIssueBaselines())
                        .set("versions.$.scanResult", null);
                update.set("versions.$.scheduledPublishDate", null);
            }
            case SCHEDULE -> {
                update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.SCHEDULED);
                update.set("versions.$.scheduledPublishDate", LocalDateTime.now().plusMinutes(routingDecision.delayMinutes()).toString());
            }
            case DEFER -> {
                update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                        .set("versions.$.scheduledPublishDate", null);
            }
            case REQUIRE_REVIEW -> {
                update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING);
                update.set("versions.$.scheduledPublishDate", null);
            }
        }

        String expectedHash = scanResult.getSecurityEvidence() == null ? null : scanResult.getSecurityEvidence().artifactSha256();
        Query target = bindRequest(buildVersionAttemptQueryBound(projectId, versionId, expectedAttempt, expectedHash, reviewedVersion, "SCANNING"), requestId);
        boolean automatic = routingDecision.action() == ScanRoutingService.RoutingAction.APPROVE_NOW
                || routingDecision.action() == ScanRoutingService.RoutingAction.SCHEDULE;
        boolean linked = scanResult.getReusedReviewVersion() != null;
        boolean validOrigins = !automatic || ArtifactReviewLineage.bind(mongoTemplate, projectId, scanResult, target);
        if (validOrigins && mongoTemplate.updateFirst(target, update, Project.class).getModifiedCount() > 0) return true;
        if (automatic && linked) {
            ArtifactReviewLineage.invalidate(scanResult);
            var hold = new Update().set("versions.$.scanResult", scanResult)
                    .set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                    .set("versions.$.scheduledPublishDate", null).set("updatedAt", LocalDateTime.now().toString());
            if (mongoTemplate.updateFirst(bindRequest(buildVersionAttemptQueryBound(projectId, versionId, expectedAttempt, expectedHash,
                    reviewedVersion, "SCANNING"), requestId), hold, Project.class).getModifiedCount() > 0)
                projectRepository.findById(projectId).ifPresent(projectService::evictProjectCache);
        }
        // A held fallback must not make the caller announce an approval.
        return false;
    }

    public boolean queueRetryAttempt(String projectId, String versionId, int currentAttempt, ScanResult queued, ScanResult observed, long timeoutMillis) {
        Query target = recoveryQuery(projectId, versionId, currentAttempt, observed, timeoutMillis);
        if (target == null) return false;
        Update retryUpdate = new Update()
                .set("versions.$.scanResult", queued)
                .set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                .set("versions.$.scheduledPublishDate", null)
                .set("updatedAt", LocalDateTime.now().toString());

        return mongoTemplate.updateFirst(
                target,
                retryUpdate,
                Project.class
        ).getModifiedCount() > 0;
    }

    public boolean updateFailedScan(String projectId, String versionId, ScanResult failed, int expectedAttempt) {
        return updateFailedScan(projectId, versionId, failed, expectedAttempt, null);
    }
    public boolean updateFailedScan(String projectId, String versionId, ScanResult failed, int expectedAttempt, String requestId) {
        failed.setScanRequestId(requestId);
        return updateFailure(projectId, failed, bindRequest(buildVersionAttemptQuery(projectId, versionId, expectedAttempt, "SCANNING", "QUEUED", "WAITING_RETRY", null), requestId));
    }
    public boolean updateTimedOutScan(String projectId, String versionId, ScanResult failed, int expectedAttempt,
            ScanResult observed, long timeoutMillis) {
        Query target = recoveryQuery(projectId, versionId, expectedAttempt, observed, timeoutMillis);
        if (observed != null) failed.setScanRequestId(observed.getScanRequestId());
        return target != null && updateFailure(projectId, failed, target);
    }
    private Query recoveryQuery(String projectId, String versionId, int attempt, ScanResult observed, long timeoutMillis) {
        if (observed == null || observed.getStatus() != ScanStatus.SCANNING || timeoutMillis <= 0
                || Math.max(1, observed.getScanAttempt()) != attempt) return null;
        String state = observed.getScanState();
        if (state != null && !java.util.Set.of("SCANNING", "QUEUED", "WAITING_RETRY").contains(state)) return null;
        long expires;
        try { expires = Math.addExact(Math.max(0, observed.getScanTimestamp()), timeoutMillis); }
        catch (ArithmeticException overflow) { return null; }
        var raw = buildVersionAttemptQuery(projectId, versionId, attempt, state).getQueryObject();
        var version = raw.get("versions", org.bson.Document.class).get("$elemMatch", org.bson.Document.class);
        version.put("scanResult.scanTimestamp", observed.getScanTimestamp() == 0
                ? new org.bson.Document("$in", java.util.Arrays.asList(0L, null)) : observed.getScanTimestamp());
        raw.put("$expr", new org.bson.Document("$lt", List.of(expires, new org.bson.Document("$toLong", "$$NOW"))));
        return bindRequest(new org.springframework.data.mongodb.core.query.BasicQuery(raw), observed.getScanRequestId());
    }
    private Query bindRequest(Query query, String requestId) {
        var raw = query.getQueryObject();
        raw.get("versions", org.bson.Document.class).get("$elemMatch", org.bson.Document.class)
                .put("scanResult.scanRequestId", requestId);
        return new org.springframework.data.mongodb.core.query.BasicQuery(raw);
    }
    private boolean updateFailure(String projectId, ScanResult failed, Query target) {
        Update update = new Update()
                .set("versions.$.scanResult", failed)
                .set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                .set("versions.$.scheduledPublishDate", null)
                .set("updatedAt", LocalDateTime.now().toString());

        boolean modified = mongoTemplate.updateFirst(target, update, Project.class)
                .getModifiedCount() > 0;

        Project project = projectRepository.findById(projectId).orElse(null);
        projectService.evictProjectCache(project);
        return modified;
    }

    public List<Project> findProjectsWithScanningVersions() {
        Query query = new Query(Criteria.where("versions").elemMatch(
                Criteria.where("scanResult.status").is(ScanStatus.SCANNING.name())
        ));
        return mongoTemplate.find(query, Project.class);
    }

    private Query buildVersionAttemptQuery(String projectId, String versionId, int attempt) {
        return buildVersionAttemptQuery(projectId, versionId, attempt, "SCANNING");
    }
    private Query buildVersionAttemptQuery(String projectId, String versionId, int attempt, String... states) {
        return buildVersionAttemptQueryBound(projectId, versionId, attempt, null, null, states);
    }
    private Query buildVersionAttemptQueryBound(String projectId, String versionId, int attempt, String expectedHash, ProjectVersion reviewedVersion, String... states) {
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

        Criteria version = Criteria.where("_id").is(versionId)
                .and("reviewStatus").is(ProjectVersion.ReviewStatus.PENDING)
                .and("scanResult.status").is(ScanStatus.SCANNING)
                .and("scanResult.scanState").in((Object[]) states)
                .andOperator(attemptCriteria);
        if (reviewedVersion != null) ArtifactReviewContext.bindSnapshot(version, reviewedVersion);
        if (expectedHash != null) version.and("hash").is(expectedHash);
        return new Query(Criteria.where("_id").is(projectId).and("versions").elemMatch(version));
    }
}
