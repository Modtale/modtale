package net.modtale.service.admin.review;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.admin.AdminAuthorStatsDTO;
import net.modtale.model.dto.admin.AdminProjectReviewDTO;
import net.modtale.model.dto.admin.AdminVerificationQueueItemDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ScanStatus;
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

    public List<AdminVerificationQueueItemDTO> getVerificationQueue() {
        return projectReviewQueueService.getVerificationQueue().stream()
                .map(ProjectMapper::toVerificationQueueItemDTO)
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(ProjectReviewQueryService::queuePriority).reversed()
                        .thenComparing(item -> item.updatedAt() == null ? "" : item.updatedAt()))
                .toList();
    }

    private static int queuePriority(AdminVerificationQueueItemDTO item) {
        if (item.pendingVersion() == null || item.pendingVersion().scan() == null) return 2_000;
        var scan = item.pendingVersion().scan();
        String verdict = scan.verdict() == null ? "" : scan.verdict().toUpperCase(Locale.ROOT);
        int riskScore = scan.riskScore();

        if ("BLOCK".equals(verdict) || scan.status() == ScanStatus.INFECTED) {
            return 8_000 + riskScore + scan.newIssueCount() * 10 + scan.escalatedIssueCount() * 15;
        }
        if (scan.newIssueCount() > 0 || scan.escalatedIssueCount() > 0) {
            return 6_000 + riskScore + scan.newIssueCount() * 6 + scan.escalatedIssueCount() * 10;
        }
        if ("REVIEW".equals(verdict)
                || scan.status() == ScanStatus.SUSPICIOUS
                || scan.status() == ScanStatus.FLAGGED) {
            return 4_000 + riskScore;
        }
        return 2_000 + riskScore;
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
