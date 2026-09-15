package net.modtale.service.admin.review;

import java.util.List;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class ProjectReviewQueueService {

    private final MongoTemplate mongoTemplate;

    public ProjectReviewQueueService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Project> getVerificationQueue() {
        Criteria reviewCandidate = new Criteria().orOperator(
                Criteria.where("status").is(ProjectStatus.PENDING),
                new Criteria().andOperator(
                        Criteria.where("status").is(ProjectStatus.PUBLISHED),
                        Criteria.where("versions").elemMatch(
                                Criteria.where("reviewStatus").is(ProjectVersion.ReviewStatus.PENDING)
                        )
                )
        );
        Criteria noVersionIsScanning = Criteria.where("versions").not().elemMatch(
                Criteria.where("scanResult.status").is(ScanStatus.SCANNING)
        );
        Query query = new Query(new Criteria().andOperator(reviewCandidate, noVersionIsScanning));

        query.fields()
                .include("id")
                .include("title")
                .include("description")
                .include("author")
                .include("imageUrl")
                .include("classification")
                .include("status")
                .include("updatedAt")
                .include("versions.id")
                .include("versions.versionNumber")
                .include("versions.changelog")
                .include("versions.reviewStatus")
                .include("versions.scanResult.status")
                .include("versions.scanResult.verdict")
                .include("versions.scanResult.riskScore")
                .include("versions.scanResult.knownIssueCount")
                .include("versions.scanResult.newIssueCount")
                .include("versions.scanResult.escalatedIssueCount");

        return mongoTemplate.find(query, Project.class);
    }
}
