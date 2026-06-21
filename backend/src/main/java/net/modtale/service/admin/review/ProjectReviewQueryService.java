package net.modtale.service.admin.review;

import java.util.List;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.admin.AdminAuthorStatsDTO;
import net.modtale.model.dto.admin.AdminProjectReviewDTO;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.query.ProjectListingQueryService;
import net.modtale.service.project.query.ProjectService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ProjectReviewQueryService {

    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final ProjectReviewQueueService projectReviewQueueService;
    private final ProjectListingQueryService projectListingQueryService;

    public ProjectReviewQueryService(
            UserRepository userRepository,
            ProjectService projectService,
            ProjectReviewQueueService projectReviewQueueService,
            ProjectListingQueryService projectListingQueryService
    ) {
        this.userRepository = userRepository;
        this.projectService = projectService;
        this.projectReviewQueueService = projectReviewQueueService;
        this.projectListingQueryService = projectListingQueryService;
    }

    public List<ProjectSummaryDTO> getVerificationQueue() {
        return projectReviewQueueService.getVerificationQueue().stream()
                .map(project -> ProjectMapper.toSummaryDTO(project, true))
                .toList();
    }

    public AdminProjectReviewDTO getProjectReviewDetails(String id) {
        Project project = requireProject(id);
        User author = userRepository.findById(project.getAuthorId()).orElse(null);

        AdminAuthorStatsDTO authorStats = new AdminAuthorStatsDTO(
                author != null ? author.getCreatedAt() : "Unknown",
                author != null ? author.getTier().name() : "Unknown",
                author != null && author.getAvatarUrl() != null ? author.getAvatarUrl() : "",
                author != null
                        ? projectListingQueryService.getCreatorProjects(author.getId(), PageRequest.of(0, 10_000)).getTotalElements()
                        : 0
        );

        return new AdminProjectReviewDTO(ProjectMapper.toAdminDTO(project), authorStats);
    }

    private Project requireProject(String id) {
        Project project = projectService.getRawProjectById(id);
        if (project == null) {
            throw new ResourceNotFoundException("Project not found.");
        }
        return project;
    }
}
