package net.modtale.service.admin.review;

import net.modtale.exception.ResourceNotFoundException;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.admin.AdminAuthorStatsDTO;
import net.modtale.model.dto.admin.AdminProjectReviewDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.query.ProjectService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class ProjectReviewQueryService {

    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final MongoTemplate mongoTemplate;

    public ProjectReviewQueryService(
            UserRepository userRepository,
            ProjectService projectService,
            MongoTemplate mongoTemplate
    ) {
        this.userRepository = userRepository;
        this.projectService = projectService;
        this.mongoTemplate = mongoTemplate;
    }

    public AdminProjectReviewDTO getProjectReviewDetails(String id) {
        Project project = requireProject(id);
        User author = userRepository.findById(project.getAuthorId()).orElse(null);

        AdminAuthorStatsDTO authorStats = new AdminAuthorStatsDTO(
                author != null ? author.getCreatedAt() : "Unknown",
                author != null ? author.getTier().name() : "Unknown",
                author != null && author.getAvatarUrl() != null ? author.getAvatarUrl() : "",
                author != null
                        ? mongoTemplate.count(new Query(Criteria.where("authorId").is(author.getId())
                                .and("status").is(ProjectStatus.PUBLISHED).and("deletedAt").is(null)), Project.class)
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
