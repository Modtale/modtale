package net.modtale.service.admin.review;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.lifecycle.LifecycleService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Service
public class ProjectReviewTransitionService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final LifecycleService lifecycleService;
    private final ScoringService scoringService;
    private final SecurityIssueAnalysisService securityIssueAnalysisService;
    private final ProjectVersionAccessService projectVersionAccessService;
    private final MongoTemplate mongoTemplate;

    public ProjectReviewTransitionService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            LifecycleService lifecycleService,
            ScoringService scoringService,
            SecurityIssueAnalysisService securityIssueAnalysisService,
            ProjectVersionAccessService projectVersionAccessService,
            MongoTemplate mongoTemplate
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.lifecycleService = lifecycleService;
        this.scoringService = scoringService;
        this.securityIssueAnalysisService = securityIssueAnalysisService;
        this.projectVersionAccessService = projectVersionAccessService;
        this.mongoTemplate = mongoTemplate;
    }

    public void publishProject(User adminUser, String id) {
        lifecycleService.publishProject(id, adminUser);
    }

    public VersionReviewDecision approveVersion(String id, String versionId) {
        Project project = requireApprovalProject(id, versionId);
        ProjectVersion version = projectVersionAccessService.requireById(project, versionId,
                () -> new ResourceNotFoundException("Version not found."));
        if (version.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED) {
            return new VersionReviewDecision(project, version, null, false);
        }
        List<ProjectVersion> versionsToUpdate = new ArrayList<>();
        for (ProjectVersion candidate : project.getVersions()) {
            if (candidate == version || (candidate.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED
                    && candidate.getScanResult() != null)) {
                versionsToUpdate.add(candidate);
            }
        }
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        version.setRejectionReason(null);
        version.setScheduledPublishDate(null);
        securityIssueAnalysisService.pruneApprovedScanResults(project);
        String updatedAt = LocalDateTime.now().toString();
        project.setUpdatedAt(updatedAt);
        Update update = new Update().set("updatedAt", updatedAt);
        for (int index = 0; index < versionsToUpdate.size(); index++) {
            ProjectVersion candidate = versionsToUpdate.get(index);
            String alias = "review" + index;
            update.set("versions.$[" + alias + "]", candidate);
            update.filterArray(Criteria.where(alias + "._id").is(candidate.getId()));
        }
        Query query = new Query(Criteria.where("_id").is(project.getId())
                .and("versions").elemMatch(Criteria.where("_id").is(versionId)
                        .and("reviewStatus").is(ProjectVersion.ReviewStatus.PENDING)));
        UpdateResult result = mongoTemplate.updateFirst(query, update, Project.class);
        if (result.getMatchedCount() == 0) {
            return new VersionReviewDecision(project, version, null, false);
        }
        projectService.evictProjectCache(project);
        return new VersionReviewDecision(project, version, null, true);
    }

    public VersionReviewDecision rejectVersion(String id, String versionId, String reason) {
        Project project = requireProject(id);
        ProjectVersion version = projectVersionAccessService.requireById(project, versionId,
                () -> new ResourceNotFoundException("Version not found."));
        version.setReviewStatus(ProjectVersion.ReviewStatus.REJECTED);
        version.setRejectionReason(reason);
        version.setScheduledPublishDate(null);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
        return new VersionReviewDecision(project, version, reason, true);
    }

    public ProjectRejectionDecision rejectProject(String id, String reason) {
        Project project = requireProject(id);
        project.setStatus(ProjectStatus.DRAFT);
        scoringService.markProjectRankingDirty(project);
        projectRepository.save(project);
        projectService.evictProjectCache(project);
        return new ProjectRejectionDecision(project, reason);
    }

    private Project requireProject(String id) {
        Project project = projectService.getRawProjectById(id);
        if (project == null) {
            throw new ResourceNotFoundException("Project not found.");
        }
        return project;
    }

    private Project requireApprovalProject(String id, String versionId) {
        Object mongoId = ObjectId.isValid(id) ? new ObjectId(id) : id;
        Document filter = new Document("$filter", new Document("input", "$versions")
                .append("as", "candidate")
                .append("cond", new Document("$or", List.of(
                        new Document("$eq", List.of("$$candidate._id", versionId)),
                        new Document("$and", List.of(
                                new Document("$eq", List.of("$$candidate.reviewStatus", "APPROVED")),
                                new Document("$ne", Arrays.asList(
                                        new Document("$ifNull", Arrays.asList("$$candidate.scanResult", null)), null))
                        ))
                ))));
        Document projection = new Document("$project", new Document("slug", 1)
                .append("title", 1).append("imageUrl", 1).append("classification", 1)
                .append("versions", filter));
        Aggregation aggregation = Aggregation.newAggregation(
                context -> new Document("$match", new Document("_id", mongoId)),
                context -> projection
        );
        Project project = mongoTemplate.aggregate(aggregation, "projects", Project.class)
                .getUniqueMappedResult();
        if (project == null && mongoId instanceof ObjectId) {
            project = requireProject(id);
        }
        if (project == null) throw new ResourceNotFoundException("Project not found.");
        return project;
    }

    public record VersionReviewDecision(Project project, ProjectVersion version, String reason, boolean changed) {
    }

    public record ProjectRejectionDecision(Project project, String reason) {
    }
}
