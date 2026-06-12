package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProjectCacheServiceTest {

    @Test
    void evictProjectCacheClearsProjectDetailsAndPublicSearchResults() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                "projectDetails",
                "projectDetailDtos",
                "projectMetaDtos",
                "projectPermissionSnapshots",
                "projectSearch",
                "projectSummarySearch",
                "sitemapData",
                "platformStats"
        );
        ProjectCacheService cacheService = new ProjectCacheService(cacheManager, new ProjectRouteService());

        Project project = new Project();
        project.setId("project-1");
        project.setSlug("sky-tools");
        project.setTitle("Sky Tools");
        project.setClassification(ProjectClassification.PLUGIN);

        cacheManager.getCache("projectDetails").put("public:project-1", project);
        cacheManager.getCache("projectDetails").put("public:sky-tools~project-1", project);
        cacheManager.getCache("projectDetailDtos").put("public:project-1", "detail-dto");
        cacheManager.getCache("projectMetaDtos").put("public:project-1", "meta-dto");
        cacheManager.getCache("projectPermissionSnapshots").put("project-1", "permission");
        cacheManager.getCache("projectSearch").put("search-page", "cached");
        cacheManager.getCache("projectSummarySearch").put("summary-page", "cached");
        cacheManager.getCache("sitemapData").put("sitemap.xml", "cached");
        cacheManager.getCache("platformStats").put("public", "cached");

        assertNotNull(cacheManager.getCache("projectDetails").get("public:project-1"));
        assertNotNull(cacheManager.getCache("projectDetails").get("public:sky-tools~project-1"));
        assertNotNull(cacheManager.getCache("projectDetailDtos").get("public:project-1"));
        assertNotNull(cacheManager.getCache("projectMetaDtos").get("public:project-1"));
        assertNotNull(cacheManager.getCache("projectPermissionSnapshots").get("project-1"));
        assertNotNull(cacheManager.getCache("projectSearch").get("search-page"));
        assertNotNull(cacheManager.getCache("projectSummarySearch").get("summary-page"));
        assertNotNull(cacheManager.getCache("sitemapData").get("sitemap.xml"));
        assertNotNull(cacheManager.getCache("platformStats").get("public"));

        cacheService.evictProjectCache(project);

        assertNull(cacheManager.getCache("projectDetails").get("public:project-1"));
        assertNull(cacheManager.getCache("projectDetails").get("public:sky-tools~project-1"));
        assertNull(cacheManager.getCache("projectDetailDtos").get("public:project-1"));
        assertNull(cacheManager.getCache("projectMetaDtos").get("public:project-1"));
        assertNull(cacheManager.getCache("projectPermissionSnapshots").get("project-1"));
        assertNull(cacheManager.getCache("projectSearch").get("search-page"));
        assertNull(cacheManager.getCache("projectSummarySearch").get("summary-page"));
        assertNull(cacheManager.getCache("sitemapData").get("sitemap.xml"));
        assertNull(cacheManager.getCache("platformStats").get("public"));
    }

    @Test
    void batchedDetailsEvictionClearsSearchOnceAndEvictsKnownIds() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                "projectDetails",
                "projectDetailDtos",
                "projectMetaDtos",
                "projectPermissionSnapshots",
                "projectSearch",
                "projectSummarySearch",
                "sitemapData",
                "platformStats"
        );
        ProjectCacheService cacheService = new ProjectCacheService(cacheManager, new ProjectRouteService());

        cacheManager.getCache("projectDetails").put("public:missing-project", "cached");
        cacheManager.getCache("projectDetailDtos").put("public:missing-project", "cached");
        cacheManager.getCache("projectMetaDtos").put("public:missing-project", "cached");
        cacheManager.getCache("projectPermissionSnapshots").put("missing-project", "cached");
        cacheManager.getCache("projectSearch").put("search-page", "cached");
        cacheManager.getCache("projectSummarySearch").put("summary-page", "cached");

        cacheService.evictProjectDetailsCaches(null, java.util.List.of("missing-project"));

        assertNull(cacheManager.getCache("projectDetails").get("public:missing-project"));
        assertNull(cacheManager.getCache("projectDetailDtos").get("public:missing-project"));
        assertNull(cacheManager.getCache("projectMetaDtos").get("public:missing-project"));
        assertNull(cacheManager.getCache("projectPermissionSnapshots").get("missing-project"));
        assertNull(cacheManager.getCache("projectSearch").get("search-page"));
        assertNull(cacheManager.getCache("projectSummarySearch").get("summary-page"));
    }
}
