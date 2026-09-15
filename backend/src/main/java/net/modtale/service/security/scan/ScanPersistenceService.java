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

        return mongoTemplate.updateFirst(buildVersionAttemptQuery(projectId, versionId, attempt), update, Project.class)
                .getModifiedCount() > 0;
    }

    public boolean applyScanOutcome(
            String projectId,
            String versionId,
            int expectedAttempt,
            ScanResult scanResult,
            ScanRoutingService.RoutingDecision routingDecision
    ) {
        Update update = new Update()
                .set("versions.$.scanResult", scanResult)
                .set("updatedAt", LocalDateTime.now().toString());

        switch (routingDecision.action()) {
            case APPROVE_NOW -> {
                update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.APPROVED);
                update.set("versions.$.scheduledPublishDate", null);
            }
            case SCHEDULE -> {
                update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.SCHEDULED);
                update.set("versions.$.scheduledPublishDate", LocalDateTime.now().plusMinutes(routingDecision.delayMinutes()).toString());
            }
            case REQUIRE_REVIEW -> {
                update.set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING);
                update.set("versions.$.scheduledPublishDate", null);
            }
        }

        return mongoTemplate.updateFirst(buildVersionAttemptQuery(projectId, versionId, expectedAttempt), update, Project.class)
                .getModifiedCount() > 0;
    }

    public boolean queueRetryAttempt(String projectId, String versionId, int currentAttempt, ScanResult queued) {
        Update retryUpdate = new Update()
                .set("versions.$.scanResult", queued)
                .set("versions.$.reviewStatus", ProjectVersion.ReviewStatus.PENDING)
                .set("versions.$.scheduledPublishDate", null)
                .set("updatedAt", LocalDateTime.now().toString());

        return mongoTemplate.updateFirst(
                buildVersionAttemptQuery(projectId, versionId, currentAttempt),
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

        boolean modified = mongoTemplate.updateFirst(buildVersionAttemptQuery(projectId, versionId, expectedAttempt), update, Project.class)
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
}
