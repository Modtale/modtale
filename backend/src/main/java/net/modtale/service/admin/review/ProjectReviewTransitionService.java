package net.modtale.service.admin.review;

import java.time.LocalDateTime;
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

@Service
public class ProjectReviewTransitionService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final LifecycleService lifecycleService;
    private final ScoringService scoringService;
    private final SecurityIssueAnalysisService securityIssueAnalysisService;
    private final ProjectVersionAccessService projectVersionAccessService;

    public ProjectReviewTransitionService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            LifecycleService lifecycleService,
            ScoringService scoringService,
            SecurityIssueAnalysisService securityIssueAnalysisService,
            ProjectVersionAccessService projectVersionAccessService
    ) {
        this.projectRepository = projectRepository;
        this.projectService = projectService;
        this.lifecycleService = lifecycleService;
        this.scoringService = scoringService;
        this.securityIssueAnalysisService = securityIssueAnalysisService;
        this.projectVersionAccessService = projectVersionAccessService;
    }

    public void publishProject(User adminUser, String id) {
        lifecycleService.publishProject(id, adminUser);
    }

    public VersionReviewDecision approveVersion(String id, String versionId) {
        Project project = requireProject(id);
        ProjectVersion version = projectVersionAccessService.requireById(project, versionId,
                () -> new ResourceNotFoundException("Version not found."));
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        version.setRejectionReason(null);
        version.setScheduledPublishDate(null);
        securityIssueAnalysisService.pruneApprovedScanResults(project);
        project.setUpdatedAt(LocalDateTime.now().toString());
        projectRepository.save(project);
        projectService.evictProjectCache(project);
        return new VersionReviewDecision(project, version, null);
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
        return new VersionReviewDecision(project, version, reason);
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

    public record VersionReviewDecision(Project project, ProjectVersion version, String reason) {
    }

    public record ProjectRejectionDecision(Project project, String reason) {
    }
}
