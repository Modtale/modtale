package net.modtale.service.project.query;

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
                "projectPageDtos",
                "projectVersionDtos",
                "projectCommentDtos",
                "projectGalleryDtos",
                "projectTeamDtos",
                "projectVersionChangelogs",
                "projectMetaDtos",
                "projectPermissionSnapshots",
                "projectSearch",
                "projectSummarySearch",
                "projectMarqueeSearch",
                "projectMarqueeSummarySearch",
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
        cacheManager.getCache("projectDetails").put("public:sky-tools", project);
        cacheManager.getCache("projectDetailDtos").put("public:project-1", "detail-dto");
        cacheManager.getCache("projectPageDtos").put("public:project-1", "page-dto");
        cacheManager.getCache("projectPageDtos").put("public:sky-tools", "page-dto");
        cacheManager.getCache("projectVersionDtos").put("public:project-1", "version-dto");
        cacheManager.getCache("projectVersionDtos").put("public:sky-tools", "version-dto");
        cacheManager.getCache("projectCommentDtos").put("public:project-1", "comment-dto");
        cacheManager.getCache("projectCommentDtos").put("public:sky-tools", "comment-dto");
        cacheManager.getCache("projectGalleryDtos").put("public:project-1", "gallery-dto");
        cacheManager.getCache("projectGalleryDtos").put("public:sky-tools", "gallery-dto");
        cacheManager.getCache("projectTeamDtos").put("public:project-1", "team-dto");
        cacheManager.getCache("projectTeamDtos").put("public:sky-tools", "team-dto");
        cacheManager.getCache("projectVersionChangelogs").put("public:project-1", "changelogs");
        cacheManager.getCache("projectVersionChangelogs").put("public:sky-tools", "changelogs");
        cacheManager.getCache("projectMetaDtos").put("public:project-1", "meta-dto");
        cacheManager.getCache("projectPermissionSnapshots").put("project-1", "permission");
        cacheManager.getCache("projectSearch").put("search-page", "cached");
        cacheManager.getCache("projectSummarySearch").put("summary-page", "cached");
        cacheManager.getCache("projectMarqueeSearch").put("marquee-page", "cached");
        cacheManager.getCache("projectMarqueeSummarySearch").put("marquee-summary-page", "cached");
        cacheManager.getCache("sitemapData").put("sitemap.xml", "cached");
        cacheManager.getCache("platformStats").put("public", "cached");

        assertNotNull(cacheManager.getCache("projectDetails").get("public:project-1"));
        assertNotNull(cacheManager.getCache("projectDetails").get("public:sky-tools"));
        assertNotNull(cacheManager.getCache("projectDetailDtos").get("public:project-1"));
        assertNotNull(cacheManager.getCache("projectPageDtos").get("public:project-1"));
        assertNotNull(cacheManager.getCache("projectPageDtos").get("public:sky-tools"));
        assertNotNull(cacheManager.getCache("projectVersionDtos").get("public:project-1"));
        assertNotNull(cacheManager.getCache("projectVersionDtos").get("public:sky-tools"));
        assertNotNull(cacheManager.getCache("projectCommentDtos").get("public:project-1"));
        assertNotNull(cacheManager.getCache("projectCommentDtos").get("public:sky-tools"));
        assertNotNull(cacheManager.getCache("projectGalleryDtos").get("public:project-1"));
        assertNotNull(cacheManager.getCache("projectGalleryDtos").get("public:sky-tools"));
        assertNotNull(cacheManager.getCache("projectTeamDtos").get("public:project-1"));
        assertNotNull(cacheManager.getCache("projectTeamDtos").get("public:sky-tools"));
        assertNotNull(cacheManager.getCache("projectVersionChangelogs").get("public:project-1"));
        assertNotNull(cacheManager.getCache("projectVersionChangelogs").get("public:sky-tools"));
        assertNotNull(cacheManager.getCache("projectMetaDtos").get("public:project-1"));
        assertNotNull(cacheManager.getCache("projectPermissionSnapshots").get("project-1"));
        assertNotNull(cacheManager.getCache("projectSearch").get("search-page"));
        assertNotNull(cacheManager.getCache("projectSummarySearch").get("summary-page"));
        assertNotNull(cacheManager.getCache("projectMarqueeSearch").get("marquee-page"));
        assertNotNull(cacheManager.getCache("projectMarqueeSummarySearch").get("marquee-summary-page"));
        assertNotNull(cacheManager.getCache("sitemapData").get("sitemap.xml"));
        assertNotNull(cacheManager.getCache("platformStats").get("public"));

        cacheService.evictProjectCache(project);

        assertNull(cacheManager.getCache("projectDetails").get("public:project-1"));
        assertNull(cacheManager.getCache("projectDetails").get("public:sky-tools"));
        assertNull(cacheManager.getCache("projectDetailDtos").get("public:project-1"));
        assertNull(cacheManager.getCache("projectPageDtos").get("public:project-1"));
        assertNull(cacheManager.getCache("projectPageDtos").get("public:sky-tools"));
        assertNull(cacheManager.getCache("projectVersionDtos").get("public:project-1"));
        assertNull(cacheManager.getCache("projectVersionDtos").get("public:sky-tools"));
        assertNull(cacheManager.getCache("projectCommentDtos").get("public:project-1"));
        assertNull(cacheManager.getCache("projectCommentDtos").get("public:sky-tools"));
        assertNull(cacheManager.getCache("projectGalleryDtos").get("public:project-1"));
        assertNull(cacheManager.getCache("projectGalleryDtos").get("public:sky-tools"));
        assertNull(cacheManager.getCache("projectTeamDtos").get("public:project-1"));
        assertNull(cacheManager.getCache("projectTeamDtos").get("public:sky-tools"));
        assertNull(cacheManager.getCache("projectVersionChangelogs").get("public:project-1"));
        assertNull(cacheManager.getCache("projectVersionChangelogs").get("public:sky-tools"));
        assertNull(cacheManager.getCache("projectMetaDtos").get("public:project-1"));
        assertNull(cacheManager.getCache("projectPermissionSnapshots").get("project-1"));
        assertNull(cacheManager.getCache("projectSearch").get("search-page"));
        assertNull(cacheManager.getCache("projectSummarySearch").get("summary-page"));
        assertNull(cacheManager.getCache("projectMarqueeSearch").get("marquee-page"));
        assertNull(cacheManager.getCache("projectMarqueeSummarySearch").get("marquee-summary-page"));
        assertNull(cacheManager.getCache("sitemapData").get("sitemap.xml"));
        assertNull(cacheManager.getCache("platformStats").get("public"));
    }

    @Test
    void batchedDetailsEvictionClearsSearchOnceAndEvictsKnownIds() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
                "projectDetails",
                "projectDetailDtos",
                "projectPageDtos",
                "projectVersionDtos",
                "projectCommentDtos",
                "projectGalleryDtos",
                "projectTeamDtos",
                "projectVersionChangelogs",
                "projectMetaDtos",
                "projectPermissionSnapshots",
                "projectSearch",
                "projectSummarySearch",
                "projectMarqueeSearch",
                "projectMarqueeSummarySearch",
                "sitemapData",
                "platformStats"
        );
        ProjectCacheService cacheService = new ProjectCacheService(cacheManager, new ProjectRouteService());

        cacheManager.getCache("projectDetails").put("public:missing-project", "cached");
        cacheManager.getCache("projectDetailDtos").put("public:missing-project", "cached");
        cacheManager.getCache("projectPageDtos").put("public:missing-project", "cached");
        cacheManager.getCache("projectVersionDtos").put("public:missing-project", "cached");
        cacheManager.getCache("projectCommentDtos").put("public:missing-project", "cached");
        cacheManager.getCache("projectGalleryDtos").put("public:missing-project", "cached");
        cacheManager.getCache("projectTeamDtos").put("public:missing-project", "cached");
        cacheManager.getCache("projectVersionChangelogs").put("public:missing-project", "cached");
        cacheManager.getCache("projectMetaDtos").put("public:missing-project", "cached");
        cacheManager.getCache("projectPermissionSnapshots").put("missing-project", "cached");
        cacheManager.getCache("projectSearch").put("search-page", "cached");
        cacheManager.getCache("projectSummarySearch").put("summary-page", "cached");
        cacheManager.getCache("projectMarqueeSearch").put("marquee-page", "cached");
        cacheManager.getCache("projectMarqueeSummarySearch").put("marquee-summary-page", "cached");

        cacheService.evictProjectDetailsCaches(null, java.util.List.of("missing-project"));

        assertNull(cacheManager.getCache("projectDetails").get("public:missing-project"));
        assertNull(cacheManager.getCache("projectDetailDtos").get("public:missing-project"));
        assertNull(cacheManager.getCache("projectPageDtos").get("public:missing-project"));
        assertNull(cacheManager.getCache("projectVersionDtos").get("public:missing-project"));
        assertNull(cacheManager.getCache("projectCommentDtos").get("public:missing-project"));
        assertNull(cacheManager.getCache("projectGalleryDtos").get("public:missing-project"));
        assertNull(cacheManager.getCache("projectTeamDtos").get("public:missing-project"));
        assertNull(cacheManager.getCache("projectVersionChangelogs").get("public:missing-project"));
        assertNull(cacheManager.getCache("projectMetaDtos").get("public:missing-project"));
        assertNull(cacheManager.getCache("projectPermissionSnapshots").get("missing-project"));
        assertNull(cacheManager.getCache("projectSearch").get("search-page"));
        assertNull(cacheManager.getCache("projectSummarySearch").get("summary-page"));
        assertNull(cacheManager.getCache("projectMarqueeSearch").get("marquee-page"));
        assertNull(cacheManager.getCache("projectMarqueeSummarySearch").get("marquee-summary-page"));
    }
}
