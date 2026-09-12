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
        Update update = new Update()
                .set("versions.$.scanResult.status", ScanStatus.SCANNING)
                .set("versions.$.scanResult.scanState", "SCANNING")
                .set("versions.$.scanResult.scanTimestamp", System.currentTimeMillis())
                .set("versions.$.scanResult.scanAttempt", attempt)
                .set("versions.$.scheduledPublishDate", null)
                .set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                .set("updatedAt", LocalDateTime.now().toString());

        return mongoTemplate.updateFirst(buildVersionAttemptQuery(projectId, versionId, attempt, "QUEUED"), update, Project.class)
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
        return mongoTemplate.updateFirst(buildVersionAttemptQueryBound(projectId, versionId, expectedAttempt, expectedHash, reviewedVersion, "SCANNING"), update, Project.class)
                .getModifiedCount() > 0;
    }

    public boolean queueRetryAttempt(String projectId, String versionId, int currentAttempt, ScanResult queued) {
        Update retryUpdate = new Update()
                .set("versions.$.scanResult", queued)
                .set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                .set("versions.$.scheduledPublishDate", null)
                .set("updatedAt", LocalDateTime.now().toString());

        return mongoTemplate.updateFirst(
                buildVersionAttemptQuery(projectId, versionId, currentAttempt, "SCANNING", "QUEUED", "WAITING_RETRY", null),
                retryUpdate,
                Project.class
        ).getModifiedCount() > 0;
    }

    public boolean updateFailedScan(String projectId, String versionId, ScanResult failed, int expectedAttempt) {
        Update update = new Update()
                .set("versions.$.scanResult", failed)
                .set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                .set("versions.$.scheduledPublishDate", null)
                .set("updatedAt", LocalDateTime.now().toString());

        boolean modified = mongoTemplate.updateFirst(buildVersionAttemptQuery(projectId, versionId, expectedAttempt, "SCANNING", "QUEUED", "WAITING_RETRY", null), update, Project.class)
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
