package net.modtale.service.admin.project;

import java.util.List;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.admin.AdminProjectDTO;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectViewCategory;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.service.admin.audit.AdminAuditLogger;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.project.query.SearchService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class ProjectAdminQueryService {

    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final SearchService searchService;
    private final AdminAuditLogger adminAuditLogger;

    public ProjectAdminQueryService(
            ProjectRepository projectRepository,
            ProjectService projectService,
            SearchService searchService,
            AdminAuditLogger adminAuditLogger
    ) {
        this.projectRepository = projectRepository;
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

    public void updateRawProject(String adminId, String id, Project updatedProject) {
        Project existing = requireProject(id);
        projectService.evictProjectCache(existing);
        updatedProject.setId(existing.getId());
        projectRepository.save(updatedProject);
        projectService.evictProjectCache(updatedProject);
        adminAuditLogger.logAction(adminId, "RAW_UPDATE_PROJECT", existing.getId(), "PROJECT", "Updated via Raw JSON");
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
