package net.modtale.service.project.query;

import java.util.Collection;
import net.modtale.model.project.Project;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

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
        Cache detailDtoCache = cacheManager.getCache("projectDetailDtos");
        Cache changelogCache = cacheManager.getCache("projectVersionChangelogs");
        Cache metaDtoCache = cacheManager.getCache("projectMetaDtos");
        Cache permissionCache = cacheManager.getCache("projectPermissionSnapshots");

        if (cache != null && project.getId() != null) cache.evict(project.getId());
        if (cache != null && project.getId() != null) cache.evict("public:" + project.getId());
        if (cache != null && project.getId() != null) cache.evict("public-page:" + project.getId());
        if (detailDtoCache != null && project.getId() != null) detailDtoCache.evict("public:" + project.getId());
        if (changelogCache != null && project.getId() != null) changelogCache.evict("public:" + project.getId());
        if (metaDtoCache != null && project.getId() != null) metaDtoCache.evict("public:" + project.getId());
        if (permissionCache != null && project.getId() != null) permissionCache.evict(project.getId());

        String routeHandle = projectRouteService.buildProjectHandle(project);
        if (cache != null && routeHandle != null) cache.evict(routeHandle);
        if (cache != null && routeHandle != null) cache.evict("public:" + routeHandle);
        if (cache != null && routeHandle != null) cache.evict("public-page:" + routeHandle);
        if (detailDtoCache != null && routeHandle != null) detailDtoCache.evict("public:" + routeHandle);
        if (changelogCache != null && routeHandle != null) changelogCache.evict("public:" + routeHandle);
        if (metaDtoCache != null && routeHandle != null) metaDtoCache.evict("public:" + routeHandle);
        if (cache != null && project.getSlug() != null) cache.evict(project.getSlug());
        if (cache != null && project.getSlug() != null) cache.evict("public:" + project.getSlug());
        if (cache != null && project.getSlug() != null) cache.evict("public-page:" + project.getSlug());
        if (detailDtoCache != null && project.getSlug() != null) detailDtoCache.evict("public:" + project.getSlug());
        if (changelogCache != null && project.getSlug() != null) changelogCache.evict("public:" + project.getSlug());
        if (metaDtoCache != null && project.getSlug() != null) metaDtoCache.evict("public:" + project.getSlug());
    }

    public void evictProjectDetailsCacheById(String projectId) {
        if (projectId == null || projectId.isBlank()) return;
        Cache cache = cacheManager.getCache("projectDetails");
        if (cache != null) {
            cache.evict(projectId);
            cache.evict("public:" + projectId);
            cache.evict("public-page:" + projectId);
        }
        Cache detailDtoCache = cacheManager.getCache("projectDetailDtos");
        if (detailDtoCache != null) {
            detailDtoCache.evict("public:" + projectId);
        }
        Cache changelogCache = cacheManager.getCache("projectVersionChangelogs");
        if (changelogCache != null) {
            changelogCache.evict("public:" + projectId);
        }
        Cache metaDtoCache = cacheManager.getCache("projectMetaDtos");
        if (metaDtoCache != null) {
            metaDtoCache.evict("public:" + projectId);
        }
        Cache permissionCache = cacheManager.getCache("projectPermissionSnapshots");
        if (permissionCache != null) {
            permissionCache.evict(projectId);
        }
    }

    public void evictProjectSearchCache() {
        clearCache("projectSearch");
        clearCache("projectSummarySearch");
        clearCache("sitemapData");
        clearCache("platformStats");
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

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
