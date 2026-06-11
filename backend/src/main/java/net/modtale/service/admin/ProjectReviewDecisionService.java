package net.modtale.service.admin;

import net.modtale.model.user.User;
import org.springframework.stereotype.Service;

@Service
public class ProjectReviewDecisionService {

    private final ProjectReviewTransitionService projectReviewTransitionService;
    private final ProjectReviewEffectService projectReviewEffectService;

    public ProjectReviewDecisionService(
            ProjectReviewTransitionService projectReviewTransitionService,
            ProjectReviewEffectService projectReviewEffectService
    ) {
        this.projectReviewTransitionService = projectReviewTransitionService;
        this.projectReviewEffectService = projectReviewEffectService;
    }

    public void publishProject(User adminUser, String id) {
        projectReviewTransitionService.publishProject(adminUser, id);
        projectReviewEffectService.onProjectPublished(adminUser, id);
    }

    public void approveVersion(User adminUser, String id, String versionId) {
        ProjectReviewTransitionService.VersionReviewDecision decision =
                projectReviewTransitionService.approveVersion(id, versionId);
        projectReviewEffectService.onVersionApproved(adminUser, id, versionId, decision);
    }

    public void rejectVersion(User adminUser, String id, String versionId, String reason) {
        ProjectReviewTransitionService.VersionReviewDecision decision =
                projectReviewTransitionService.rejectVersion(id, versionId, reason);
        projectReviewEffectService.onVersionRejected(adminUser, id, versionId, decision);
    }

    public void rejectProject(User adminUser, String id, String reason) {
        ProjectReviewTransitionService.ProjectRejectionDecision decision =
                projectReviewTransitionService.rejectProject(id, reason);
        projectReviewEffectService.onProjectRejected(adminUser, id, decision);
    }
}
