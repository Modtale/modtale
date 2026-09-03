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
        Cache pageDtoCache = cacheManager.getCache("projectPageDtos");
        Cache versionDtoCache = cacheManager.getCache("projectVersionDtos");
        Cache commentDtoCache = cacheManager.getCache("projectCommentDtos");
        Cache galleryDtoCache = cacheManager.getCache("projectGalleryDtos");
        Cache teamDtoCache = cacheManager.getCache("projectTeamDtos");
        Cache changelogCache = cacheManager.getCache("projectVersionChangelogs");
        Cache metaDtoCache = cacheManager.getCache("projectMetaDtos");
        Cache permissionCache = cacheManager.getCache("projectPermissionSnapshots");
        Cache wikiProjectJsonCache = cacheManager.getCache("wikiProjectJson");
        Cache wikiPageJsonCache = cacheManager.getCache("wikiPageJson");
        Cache wikiPageBundleJsonCache = cacheManager.getCache("wikiPageBundleJson");

        if (cache != null && project.getId() != null) cache.evict(project.getId());
        if (cache != null && project.getId() != null) cache.evict("public:" + project.getId());
        if (cache != null && project.getId() != null) cache.evict("public-page:" + project.getId());
        if (detailDtoCache != null && project.getId() != null) detailDtoCache.evict("public:" + project.getId());
        if (pageDtoCache != null && project.getId() != null) pageDtoCache.evict("public:" + project.getId());
        if (versionDtoCache != null && project.getId() != null) versionDtoCache.evict("public:" + project.getId());
        if (commentDtoCache != null && project.getId() != null) commentDtoCache.evict("public:" + project.getId());
        if (galleryDtoCache != null && project.getId() != null) galleryDtoCache.evict("public:" + project.getId());
        if (teamDtoCache != null && project.getId() != null) teamDtoCache.evict("public:" + project.getId());
        if (changelogCache != null && project.getId() != null) changelogCache.evict("public:" + project.getId());
        if (metaDtoCache != null && project.getId() != null) metaDtoCache.evict("public:" + project.getId());
        if (permissionCache != null && project.getId() != null) permissionCache.evict(project.getId());
        if (wikiProjectJsonCache != null && project.getId() != null) wikiProjectJsonCache.evict("public:" + project.getId());

        String routeHandle = projectRouteService.buildProjectHandle(project);
        if (cache != null && routeHandle != null) cache.evict(routeHandle);
        if (cache != null && routeHandle != null) cache.evict("public:" + routeHandle);
        if (cache != null && routeHandle != null) cache.evict("public-page:" + routeHandle);
        if (detailDtoCache != null && routeHandle != null) detailDtoCache.evict("public:" + routeHandle);
        if (pageDtoCache != null && routeHandle != null) pageDtoCache.evict("public:" + routeHandle);
        if (versionDtoCache != null && routeHandle != null) versionDtoCache.evict("public:" + routeHandle);
        if (commentDtoCache != null && routeHandle != null) commentDtoCache.evict("public:" + routeHandle);
        if (galleryDtoCache != null && routeHandle != null) galleryDtoCache.evict("public:" + routeHandle);
        if (teamDtoCache != null && routeHandle != null) teamDtoCache.evict("public:" + routeHandle);
        if (changelogCache != null && routeHandle != null) changelogCache.evict("public:" + routeHandle);
        if (metaDtoCache != null && routeHandle != null) metaDtoCache.evict("public:" + routeHandle);
        if (wikiProjectJsonCache != null && routeHandle != null) wikiProjectJsonCache.evict("public:" + routeHandle);
        if (cache != null && project.getSlug() != null) cache.evict(project.getSlug());
        if (cache != null && project.getSlug() != null) cache.evict("public:" + project.getSlug());
        if (cache != null && project.getSlug() != null) cache.evict("public-page:" + project.getSlug());
        if (detailDtoCache != null && project.getSlug() != null) detailDtoCache.evict("public:" + project.getSlug());
        if (pageDtoCache != null && project.getSlug() != null) pageDtoCache.evict("public:" + project.getSlug());
        if (versionDtoCache != null && project.getSlug() != null) versionDtoCache.evict("public:" + project.getSlug());
        if (commentDtoCache != null && project.getSlug() != null) commentDtoCache.evict("public:" + project.getSlug());
        if (galleryDtoCache != null && project.getSlug() != null) galleryDtoCache.evict("public:" + project.getSlug());
        if (teamDtoCache != null && project.getSlug() != null) teamDtoCache.evict("public:" + project.getSlug());
        if (changelogCache != null && project.getSlug() != null) changelogCache.evict("public:" + project.getSlug());
        if (metaDtoCache != null && project.getSlug() != null) metaDtoCache.evict("public:" + project.getSlug());
        if (wikiProjectJsonCache != null && project.getSlug() != null) wikiProjectJsonCache.evict("public:" + project.getSlug());
        if (wikiPageJsonCache != null) wikiPageJsonCache.clear();
        if (wikiPageBundleJsonCache != null) wikiPageBundleJsonCache.clear();
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
        Cache pageDtoCache = cacheManager.getCache("projectPageDtos");
        if (pageDtoCache != null) {
            pageDtoCache.evict("public:" + projectId);
        }
        Cache versionDtoCache = cacheManager.getCache("projectVersionDtos");
        if (versionDtoCache != null) {
            versionDtoCache.evict("public:" + projectId);
        }
        Cache commentDtoCache = cacheManager.getCache("projectCommentDtos");
        if (commentDtoCache != null) {
            commentDtoCache.evict("public:" + projectId);
        }
        Cache galleryDtoCache = cacheManager.getCache("projectGalleryDtos");
        if (galleryDtoCache != null) {
            galleryDtoCache.evict("public:" + projectId);
        }
        Cache teamDtoCache = cacheManager.getCache("projectTeamDtos");
        if (teamDtoCache != null) {
            teamDtoCache.evict("public:" + projectId);
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
        Cache wikiProjectJsonCache = cacheManager.getCache("wikiProjectJson");
        if (wikiProjectJsonCache != null) {
            wikiProjectJsonCache.evict("public:" + projectId);
        }
        clearCache("wikiPageJson");
        clearCache("wikiPageBundleJson");
    }

    public void evictProjectSearchCache() {
        clearCache("projectSearch");
        clearCache("projectSummarySearch");
        clearCache("projectMarqueeSearch");
        clearCache("projectMarqueeSummarySearch");
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
