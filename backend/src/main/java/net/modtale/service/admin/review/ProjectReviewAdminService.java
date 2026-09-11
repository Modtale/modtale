package net.modtale.service.admin.review;

import java.util.List;
import net.modtale.model.dto.admin.AdminProjectReviewDTO;
import net.modtale.model.dto.admin.AdminVerificationQueueItemDTO;
import net.modtale.model.user.User;
import org.springframework.stereotype.Service;

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

    public List<AdminVerificationQueueItemDTO> getVerificationQueue() {
        return projectReviewQueryService.getVerificationQueue();
    }

    public AdminProjectReviewDTO getProjectReviewDetails(String id) {
        return projectReviewQueryService.getProjectReviewDetails(id);
    }

    public void publishProject(User adminUser, String id) {
        projectReviewDecisionService.publishProject(adminUser, id);
    }

    public void approveVersion(User adminUser, String id, String versionId, String reviewToken) {
        projectReviewDecisionService.approveVersion(adminUser, id, versionId, reviewToken);
    }

    public void rejectVersion(User adminUser, String id, String versionId, String reason, String reviewToken) {
        projectReviewDecisionService.rejectVersion(adminUser, id, versionId, reason, reviewToken);
    }

    public void rejectProject(User adminUser, String id, String reason) {
        projectReviewDecisionService.rejectProject(adminUser, id, reason);
    }
}
