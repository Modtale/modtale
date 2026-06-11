package net.modtale.service.admin;

import net.modtale.model.dto.admin.AdminProjectDTO;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectAdminOperationsService {

    private final ProjectAdminQueryService projectAdminQueryService;
    private final ProjectModerationService projectModerationService;

    public ProjectAdminOperationsService(
            ProjectAdminQueryService projectAdminQueryService,
            ProjectModerationService projectModerationService
    ) {
        this.projectAdminQueryService = projectAdminQueryService;
        this.projectModerationService = projectModerationService;
    }

    public AdminProjectDTO getProjectById(String id) {
        return projectAdminQueryService.getProjectById(id);
    }

    public void updateRawProject(String adminId, String id, Project updatedProject) {
        projectAdminQueryService.updateRawProject(adminId, id, updatedProject);
    }

    public void deleteProject(net.modtale.model.user.User adminUser, String id, String reason) {
        projectModerationService.deleteProject(adminUser, id, reason);
    }

    public void hardDeleteProject(net.modtale.model.user.User adminUser, String id, String reason) {
        projectModerationService.hardDeleteProject(adminUser, id, reason);
    }

    public void restoreProject(net.modtale.model.user.User adminUser, String id, ProjectStatus targetStatus) {
        projectModerationService.restoreProject(adminUser, id, targetStatus);
    }

    public void unlistProject(net.modtale.model.user.User adminUser, String id, String reason) {
        projectModerationService.unlistProject(adminUser, id, reason);
    }

    public void deleteProjectVersion(net.modtale.model.user.User adminUser, String id, String versionId) {
        projectModerationService.deleteProjectVersion(adminUser, id, versionId);
    }

    public List<ProjectSummaryDTO> searchProjects(String query, boolean deleted) {
        return projectAdminQueryService.searchProjects(query, deleted);
    }

    public void rescanVersion(net.modtale.model.user.User adminUser, String id, String versionId) {
        projectModerationService.rescanVersion(adminUser, id, versionId);
    }
}
