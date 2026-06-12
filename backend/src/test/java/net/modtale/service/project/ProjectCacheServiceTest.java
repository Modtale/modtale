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
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("projectDetails", "projectSearch");
        ProjectCacheService cacheService = new ProjectCacheService(cacheManager, new ProjectRouteService());

        Project project = new Project();
        project.setId("project-1");
        project.setSlug("sky-tools");
        project.setTitle("Sky Tools");
        project.setClassification(ProjectClassification.PLUGIN);

        cacheManager.getCache("projectDetails").put("public:project-1", project);
        cacheManager.getCache("projectDetails").put("public:sky-tools~project-1", project);
        cacheManager.getCache("projectSearch").put("search-page", "cached");

        assertNotNull(cacheManager.getCache("projectDetails").get("public:project-1"));
        assertNotNull(cacheManager.getCache("projectDetails").get("public:sky-tools~project-1"));
        assertNotNull(cacheManager.getCache("projectSearch").get("search-page"));

        cacheService.evictProjectCache(project);

        assertNull(cacheManager.getCache("projectDetails").get("public:project-1"));
        assertNull(cacheManager.getCache("projectDetails").get("public:sky-tools~project-1"));
        assertNull(cacheManager.getCache("projectSearch").get("search-page"));
    }

    @Test
    void batchedDetailsEvictionClearsSearchOnceAndEvictsKnownIds() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("projectDetails", "projectSearch");
        ProjectCacheService cacheService = new ProjectCacheService(cacheManager, new ProjectRouteService());

        cacheManager.getCache("projectDetails").put("public:missing-project", "cached");
        cacheManager.getCache("projectSearch").put("search-page", "cached");

        cacheService.evictProjectDetailsCaches(null, java.util.List.of("missing-project"));

        assertNull(cacheManager.getCache("projectDetails").get("public:missing-project"));
        assertNull(cacheManager.getCache("projectSearch").get("search-page"));
    }
}
