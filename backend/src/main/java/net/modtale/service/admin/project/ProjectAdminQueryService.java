package net.modtale.service.admin.project;

import java.util.List;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.admin.AdminProjectDTO;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectViewCategory;
import net.modtale.service.admin.review.ProjectReviewPersistence;
import net.modtale.service.admin.review.ProjectReviewSnapshot;
import net.modtale.service.admin.audit.AdminAuditLogger;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.project.query.SearchService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ProjectAdminQueryService {

    private final ProjectReviewPersistence reviewPersistence;
    private final ProjectService projectService;
    private final SearchService searchService;
    private final AdminAuditLogger adminAuditLogger;

    public ProjectAdminQueryService(
            ProjectReviewPersistence reviewPersistence,
            ProjectService projectService,
            SearchService searchService,
            AdminAuditLogger adminAuditLogger
    ) {
        this.reviewPersistence = reviewPersistence;
        this.projectService = projectService;
        this.searchService = searchService;
        this.adminAuditLogger = adminAuditLogger;
    }

    public AdminProjectDTO getProjectById(String id) {
        Project project = projectService.getAdminProjectDetails(id);
        if (project == null) {
            throw new ResourceNotFoundException("Project not found.");
        }
        return ProjectMapper.toAdminDTO(project);
    }

    public void updateRawProject(String adminId, String id, java.util.Map<String, Object> metadata, String token) {
        ProjectMetadataRepair.validate(metadata);
        var snapshot = reviewPersistence.capture(id, token);
        if (!reviewPersistence.applyMetadataRepair(snapshot, metadata)) throw ProjectReviewSnapshot.conflict();
        projectService.evictProjectCache(snapshot.project());
        adminAuditLogger.logAction(adminId, "RAW_UPDATE_PROJECT", id, "PROJECT",
                "Repaired metadata fields: " + String.join(", ", new java.util.TreeSet<>(metadata.keySet())));
    }

    public List<ProjectSummaryDTO> searchProjects(String query, boolean deleted) {
        if (deleted) {
            return searchService.searchDeletedProjects(query, PageRequest.of(0, 10)).getContent().stream()
                    .map(project -> ProjectMapper.toSummaryDTO(project, true))
                    .toList();
        }
        return searchService.searchProjects(null, query, 0, 10, ProjectSort.RELEVANCE, null, null, null, null, ProjectViewCategory.ALL, null, null, null, null)
                .getContent().stream()
                .map(project -> ProjectMapper.toSummaryDTO(project, true))
                .toList();
    }

    private Project requireProject(String id) {
        Project project = projectService.getRawProjectById(id);
        if (project == null) {
            throw new ResourceNotFoundException("Project not found.");
        }
        return project;
    }
}
