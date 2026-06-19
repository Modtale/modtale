package net.modtale.service.project.query;

import java.util.Collection;
import net.modtale.model.dto.project.ProjectVersionChangelogDTO;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ProjectService {

    private final ProjectViewService projectViewService;
    private final ProjectCacheService projectCacheService;
    private final ProjectRouteService projectRouteService;

    public ProjectService(
            ProjectViewService projectViewService,
            ProjectCacheService projectCacheService,
            ProjectRouteService projectRouteService
    ) {
        this.projectViewService = projectViewService;
        this.projectCacheService = projectCacheService;
        this.projectRouteService = projectRouteService;
    }

    public void evictProjectCache(Project project) {
        projectCacheService.evictProjectCache(project);
    }

    public void evictProjectDetailsCaches(Collection<Project> projects, Collection<String> fallbackProjectIds) {
        projectCacheService.evictProjectDetailsCaches(projects, fallbackProjectIds);
    }

    public Project getRawProjectById(String id) {
        return projectViewService.getRawProjectById(id);
    }

    public Project getRawProjectByRouteKey(String routeKey) {
        return projectViewService.getRawProjectByRouteKey(routeKey);
    }

    public Project getProjectById(String id) {
        return getPublicProjectById(id);
    }

    public Project getProjectById(String id, User viewer) {
        if (viewer == null) {
            return projectViewService.getPublicProjectById(id);
        }
        return projectViewService.getProjectById(id, viewer);
    }

    public Project getProjectByRouteKey(String routeKey, User viewer) {
        if (viewer == null) {
            return projectViewService.getPublicProjectByRouteKey(routeKey);
        }
        return projectViewService.getProjectByRouteKey(routeKey, viewer);
    }

    public Project getProjectDetailsByRouteKey(String routeKey, User viewer) {
        if (viewer == null) {
            return projectViewService.getPublicProjectDetailsByRouteKey(routeKey);
        }
        return projectViewService.getProjectDetailsByRouteKey(routeKey, viewer);
    }

    public Project getProjectPageShellByRouteKey(String routeKey, User viewer) {
        if (viewer == null) {
            return projectViewService.getPublicProjectPageShellByRouteKey(routeKey);
        }
        return projectViewService.getProjectPageShellByRouteKey(routeKey, viewer);
    }

    public Project getProjectVersionsByRouteKey(String routeKey, User viewer) {
        return projectViewService.getProjectVersionsByRouteKey(routeKey, viewer);
    }

    public Project getProjectCommentsByRouteKey(String routeKey, User viewer) {
        return projectViewService.getProjectCommentsByRouteKey(routeKey, viewer);
    }

    public Project getProjectGalleryByRouteKey(String routeKey, User viewer) {
        return projectViewService.getProjectGalleryByRouteKey(routeKey, viewer);
    }

    public Project getProjectTeamByRouteKey(String routeKey, User viewer) {
        return projectViewService.getProjectTeamByRouteKey(routeKey, viewer);
    }

    @Cacheable(value = "projectDetails", key = "'public:' + #id")
    public Project getPublicProjectById(String id) {
        return projectViewService.getPublicProjectById(id);
    }

    public Project getPublicProjectByRouteKey(String routeKey) {
        return projectViewService.getPublicProjectByRouteKey(routeKey);
    }

    public Project getPublicProjectDetailsById(String id) {
        return projectViewService.getPublicProjectDetailsById(id);
    }

    public Project getPublicProjectDetailsByRouteKey(String routeKey) {
        return projectViewService.getPublicProjectDetailsByRouteKey(routeKey);
    }

    public Project getPublicProjectPageShellByRouteKey(String routeKey) {
        return projectViewService.getPublicProjectPageShellByRouteKey(routeKey);
    }

    public Project getPublicProjectVersionsByRouteKey(String routeKey) {
        return projectViewService.getPublicProjectVersionsByRouteKey(routeKey);
    }

    public Project getPublicProjectCommentsByRouteKey(String routeKey) {
        return projectViewService.getPublicProjectCommentsByRouteKey(routeKey);
    }

    public Project getPublicProjectGalleryByRouteKey(String routeKey) {
        return projectViewService.getPublicProjectGalleryByRouteKey(routeKey);
    }

    public Project getPublicProjectTeamByRouteKey(String routeKey) {
        return projectViewService.getPublicProjectTeamByRouteKey(routeKey);
    }

    public java.util.List<ProjectVersionChangelogDTO> getVersionChangelogsByRouteKey(String routeKey, User viewer) {
        return projectViewService.getVersionChangelogsByRouteKey(routeKey, viewer);
    }

    public Project getAdminProjectDetails(String id) {
        return projectViewService.getAdminProjectDetails(id);
    }

    public Project getAdminProjectDetailsByRouteKey(String routeKey) {
        return projectViewService.getAdminProjectDetailsByRouteKey(routeKey);
    }

    public String getProjectLink(Project project) {
        return projectRouteService.getProjectLink(project);
    }
}
