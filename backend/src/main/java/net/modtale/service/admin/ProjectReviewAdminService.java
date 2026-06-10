package net.modtale.service.admin;

import net.modtale.model.dto.admin.AdminProjectReviewDTO;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.user.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectReviewAdminService {

    private final ProjectReviewQueryService projectReviewQueryService;
    private final ProjectReviewDecisionService projectReviewDecisionService;

    public ProjectReviewAdminService(
            ProjectReviewQueryService projectReviewQueryService,
            ProjectReviewDecisionService projectReviewDecisionService
    ) {
        this.projectReviewQueryService = projectReviewQueryService;
        this.projectReviewDecisionService = projectReviewDecisionService;
    }

    public List<ProjectSummaryDTO> getVerificationQueue() {
        return projectReviewQueryService.getVerificationQueue();
    }

    public AdminProjectReviewDTO getProjectReviewDetails(String id) {
        return projectReviewQueryService.getProjectReviewDetails(id);
    }

    public void publishProject(User adminUser, String id) {
        projectReviewDecisionService.publishProject(adminUser, id);
    }

    public void approveVersion(User adminUser, String id, String versionId) {
        projectReviewDecisionService.approveVersion(adminUser, id, versionId);
    }

    public void rejectVersion(User adminUser, String id, String versionId, String reason) {
        projectReviewDecisionService.rejectVersion(adminUser, id, versionId, reason);
    }

    public void rejectProject(User adminUser, String id, String reason) {
        projectReviewDecisionService.rejectProject(adminUser, id, reason);
    }
}
