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

    public Project getProjectPageByRouteKey(String routeKey, User viewer) {
        if (viewer == null) {
            return projectViewService.getPublicProjectPageByRouteKey(routeKey);
        }
        return projectViewService.getProjectPageByRouteKey(routeKey, viewer);
    }

    @Cacheable(value = "projectDetails", key = "'public:' + #id")
    public Project getPublicProjectById(String id) {
        return projectViewService.getPublicProjectById(id);
    }

    public Project getPublicProjectByRouteKey(String routeKey) {
        return projectViewService.getPublicProjectByRouteKey(routeKey);
    }

    public Project getPublicProjectPageById(String id) {
        return projectViewService.getPublicProjectPageById(id);
    }

    public Project getPublicProjectPageByRouteKey(String routeKey) {
        return projectViewService.getPublicProjectPageByRouteKey(routeKey);
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
