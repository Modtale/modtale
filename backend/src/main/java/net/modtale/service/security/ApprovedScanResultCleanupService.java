package net.modtale.service.security;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApprovedScanResultCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(ApprovedScanResultCleanupService.class);
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final MongoTemplate mongoTemplate;
    private final ProjectService projectService;
    private final SecurityIssueAnalysisService securityIssueAnalysisService;

    public ApprovedScanResultCleanupService(
            MongoTemplate mongoTemplate,
            ProjectService projectService,
            SecurityIssueAnalysisService securityIssueAnalysisService
    ) {
        this.mongoTemplate = mongoTemplate;
        this.projectService = projectService;
        this.securityIssueAnalysisService = securityIssueAnalysisService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void pruneApprovedScanResultsOnStartup() {
        int totalPruned = 0;
        while (true) {
            int pruned = pruneBatch(DEFAULT_BATCH_SIZE);
            if (pruned == 0) {
                break;
            }
            totalPruned += pruned;
            logger.info("Pruned scan results from {} approved project versions", pruned);
        }
        logger.info("Startup approved scan result cleanup finished. Total versions pruned={}", totalPruned);
    }

    int pruneBatch(int batchSize) {
        Query query = new Query(Criteria.where("versions").elemMatch(approvedVersionWithScanResultCriteria()))
                .limit(Math.max(1, batchSize));
        query.fields()
                .include("_id")
                .include("slug")
                .include("title")
                .include("versions._id")
                .include("versions.versionNumber")
                .include("versions.releaseDate")
                .include("versions.reviewStatus")
                .include("versions.scanResult");
        List<Project> projects = mongoTemplate.find(query, Project.class);

        int pruned = 0;
        for (Project project : projects) {
            boolean projectChanged = false;
            if (project.getVersions() == null) {
                continue;
            }

            for (ProjectVersion version : project.getVersions()) {
                if (!shouldPrune(version)) {
                    continue;
                }

                securityIssueAnalysisService.markIssuesAcceptedForApprovedVersion(version);
                Update update = new Update()
                        .set("versions.$.approvedIssueBaselines", version.getApprovedIssueBaselines())
                        .unset("versions.$.scanResult");

                long modified = mongoTemplate.updateFirst(
                        versionPruneQuery(project.getId(), version.getId()),
                        update,
                        Project.class
                ).getModifiedCount();

                if (modified > 0) {
                    pruned++;
                    projectChanged = true;
                }
            }

            if (projectChanged) {
                projectService.evictProjectCache(project);
            }
        }

        return pruned;
    }

    private Query versionPruneQuery(String projectId, String versionId) {
        return new Query(Criteria.where("_id").is(projectId)
                .and("versions").elemMatch(
                        Criteria.where("_id").is(versionId)
                                .andOperator(approvedVersionWithScanResultCriteria())
                ));
    }

    private Criteria approvedVersionWithScanResultCriteria() {
        return Criteria.where("reviewStatus").is(ProjectVersion.ReviewStatus.APPROVED.name())
                .and("scanResult").exists(true).ne(null);
    }

    private boolean shouldPrune(ProjectVersion version) {
        return version != null
                && version.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED
                && version.getScanResult() != null;
    }
}
