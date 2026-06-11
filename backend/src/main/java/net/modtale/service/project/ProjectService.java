package net.modtale.service.project;

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

    public Project getRawProjectById(String id) {
        return projectViewService.getRawProjectById(id);
    }

    public Project getProjectById(String id) {
        return getPublicProjectById(id);
    }

    public Project getProjectById(String id, User viewer) {
        return projectViewService.getProjectById(id, viewer);
    }

    @Cacheable(value = "projectDetails", key = "'public:' + #id")
    public Project getPublicProjectById(String id) {
        return projectViewService.getPublicProjectById(id);
    }

    public Project getAdminProjectDetails(String id) {
        return projectViewService.getAdminProjectDetails(id);
    }

    public String getProjectLink(Project project) {
        return projectRouteService.getProjectLink(project);
    }
}
