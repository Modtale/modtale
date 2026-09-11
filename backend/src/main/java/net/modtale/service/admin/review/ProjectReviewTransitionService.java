package net.modtale.service.admin.review;

import java.time.LocalDateTime;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.lifecycle.LifecycleService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import org.springframework.stereotype.Service;

@Service
public class ProjectReviewTransitionService {

    private final ProjectReviewPersistence projectReviewPersistence;
    private final VersionReviewPersistence reviewPersistence;
    private final ProjectService projectService;
    private final LifecycleService lifecycleService;
    private final ScoringService scoringService;
    private final SecurityIssueAnalysisService securityIssueAnalysisService;
    private final ProjectVersionAccessService projectVersionAccessService;

    public ProjectReviewTransitionService(
            ProjectService projectService,
            LifecycleService lifecycleService,
            ScoringService scoringService,
            SecurityIssueAnalysisService securityIssueAnalysisService,
            ProjectVersionAccessService projectVersionAccessService,
            VersionReviewPersistence reviewPersistence,
            ProjectReviewPersistence projectReviewPersistence
    ) {
        this.projectReviewPersistence = projectReviewPersistence;
        this.reviewPersistence = reviewPersistence;
        this.projectService = projectService;
        this.lifecycleService = lifecycleService;
        this.scoringService = scoringService;
        this.securityIssueAnalysisService = securityIssueAnalysisService;
        this.projectVersionAccessService = projectVersionAccessService;
    }

    public void publishProject(User adminUser, String id, String reviewToken, String versionId) {
        lifecycleService.publishProject(id, adminUser, reviewToken, versionId);
    }

    public VersionReviewDecision approveVersion(String id, String versionId, String reviewToken) {
        Project project = requireProject(id);
        ProjectVersion version = projectVersionAccessService.requireById(project, versionId,
                () -> new ResourceNotFoundException("Version not found."));
        VersionReviewSnapshot.requireCurrent(version,reviewToken);
        var snapshot=reviewPersistence.capture(id,versionId,reviewToken);
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        version.setRejectionReason(null);
        version.setScheduledPublishDate(null);
        securityIssueAnalysisService.markIssuesAcceptedForApprovedVersion(version);
        project.setUpdatedAt(LocalDateTime.now().toString());
        if(!reviewPersistence.apply(snapshot,version)) throw VersionReviewPersistence.conflict();
        projectService.evictProjectCache(project);
        return new VersionReviewDecision(project, version, null);
    }

    public VersionReviewDecision rejectVersion(String id, String versionId, String reason, String reviewToken) {
        Project project = requireProject(id);
        ProjectVersion version = projectVersionAccessService.requireById(project, versionId,
                () -> new ResourceNotFoundException("Version not found."));
        VersionReviewSnapshot.requireCurrent(version,reviewToken);
        var snapshot=reviewPersistence.capture(id,versionId,reviewToken);
        version.setReviewStatus(ProjectVersion.ReviewStatus.REJECTED);
        version.setRejectionReason(reason);
        version.setApprovedSecurityEvidence(null);
        version.setApprovedSecurityContextSha256(null);
        version.setSecurityApprovedAt(0);
        version.setApprovedIssueBaselines(null);
        version.setScheduledPublishDate(null);
        if(!reviewPersistence.apply(snapshot,version)) throw VersionReviewPersistence.conflict();
        projectService.evictProjectCache(project);
        return new VersionReviewDecision(project, version, reason);
    }

    public ProjectRejectionDecision rejectProject(String id, String reason, String reviewToken) {
        var snapshot = projectReviewPersistence.capture(id, reviewToken);
        Project project = snapshot.project();
        if (project.getStatus() != ProjectStatus.PENDING) throw ProjectReviewSnapshot.conflict();
        project.setStatus(ProjectStatus.DRAFT);
        scoringService.markProjectRankingDirty(project);
        project.setUpdatedAt(LocalDateTime.now().toString());
        if (!projectReviewPersistence.apply(snapshot, null)) throw ProjectReviewSnapshot.conflict();
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

    public record VersionReviewDecision(Project project, ProjectVersion version, String reason) {
    }

    public record ProjectRejectionDecision(Project project, String reason) {
    }
}
