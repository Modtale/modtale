package net.modtale.service.project;

import net.modtale.model.project.Project;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class ProjectCacheService {

    private final CacheManager cacheManager;
    private final ProjectRouteService projectRouteService;

    public ProjectCacheService(CacheManager cacheManager, ProjectRouteService projectRouteService) {
        this.cacheManager = cacheManager;
        this.projectRouteService = projectRouteService;
    }

    public void evictProjectCache(Project project) {
        evictProjectDetailsCache(project);
        evictProjectSearchCache();
    }

    public void evictProjectDetailsCache(Project project) {
        if (project == null) return;
        Cache cache = cacheManager.getCache("projectDetails");
        if (cache == null) return;

        if (project.getId() != null) cache.evict(project.getId());
        if (project.getId() != null) cache.evict("public:" + project.getId());

        String routeHandle = projectRouteService.buildProjectHandle(project);
        if (routeHandle != null) cache.evict(routeHandle);
        if (routeHandle != null) cache.evict("public:" + routeHandle);
        if (project.getSlug() != null) cache.evict(project.getSlug());
        if (project.getSlug() != null) cache.evict("public:" + project.getSlug());
    }

    public void evictProjectDetailsCacheById(String projectId) {
        if (projectId == null || projectId.isBlank()) return;
        Cache cache = cacheManager.getCache("projectDetails");
        if (cache == null) return;

        cache.evict(projectId);
        cache.evict("public:" + projectId);
    }

    public void evictProjectSearchCache() {
        Cache cache = cacheManager.getCache("projectSearch");
        if (cache != null) {
            cache.clear();
        }
    }

    public void evictProjectDetailsCaches(Collection<Project> projects, Collection<String> fallbackProjectIds) {
        if (projects != null) {
            projects.forEach(this::evictProjectDetailsCache);
        }
        if (fallbackProjectIds != null) {
            fallbackProjectIds.forEach(this::evictProjectDetailsCacheById);
        }
        evictProjectSearchCache();
    }
}
