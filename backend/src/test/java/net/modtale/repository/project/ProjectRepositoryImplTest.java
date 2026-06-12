package net.modtale.repository.project;

import java.util.List;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectViewCategory;
import net.modtale.service.project.query.ProjectSearchResultDecorator;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectRepositoryImplTest {

    private MongoTemplate mongoTemplate;
    private ProjectSearchResultDecorator projectSearchResultDecorator;
    private ProjectRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        projectSearchResultDecorator = mock(ProjectSearchResultDecorator.class);
        repository = new ProjectRepositoryImpl(mongoTemplate, projectSearchResultDecorator);
        when(mongoTemplate.find(org.mockito.ArgumentMatchers.any(Query.class), eq(Project.class))).thenReturn(List.of());
    }

    @Test
    void popularSortUsesRankWithFallbacksWithoutFilteringUnrankedProjects() {
        repository.searchProjects(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20),
                null,
                ProjectSort.POPULAR,
                ProjectViewCategory.ALL,
                null,
                null
        );

        Query query = capturedFindQuery();
        Document sort = query.getSortObject();
        String queryJson = query.getQueryObject().toString();

        assertEquals(1, sort.get("popularRank"));
        assertEquals(-1, sort.get("popularScore"));
        assertEquals(-1, sort.get("downloadCount"));
        assertEquals(-1, sort.get("favoriteCount"));
        assertFalse(queryJson.contains("popularRank"));
    }

    @Test
    void hiddenGemsSortUsesRankWithFallbacksWithoutFilteringUnrankedProjects() {
        repository.searchProjects(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20),
                null,
                ProjectSort.RELEVANCE,
                ProjectViewCategory.HIDDEN_GEMS,
                null,
                null
        );

        Query query = capturedFindQuery();
        Document sort = query.getSortObject();
        String queryJson = query.getQueryObject().toString();

        assertEquals(1, sort.get("hiddenGemRank"));
        assertEquals(-1, sort.get("hiddenGemScore"));
        assertEquals(-1, sort.get("favoriteCount"));
        assertFalse(sort.containsKey("downloadCount"));
        assertFalse(queryJson.contains("hiddenGemRank"));
        assertFalse(queryJson.contains("downloads30d"));
    }

    @Test
    void relevanceSortDoesNotFilterOutUnrankedProjects() {
        repository.searchProjects(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20),
                null,
                ProjectSort.RELEVANCE,
                ProjectViewCategory.ALL,
                null,
                null
        );

        Query query = capturedFindQuery();
        Document sort = query.getSortObject();
        String queryJson = query.getQueryObject().toString();

        assertEquals(1, sort.get("relevanceRank"));
        assertEquals(-1, sort.get("relevanceScore"));
        assertEquals(-1, sort.get("downloads30d"));
        assertEquals(-1, sort.get("updatedAt"));
        assertFalse(queryJson.contains("relevanceRank"));
    }

    @Test
    void trendingSortUsesTrendingSignalsInsteadOfFallingBackToUpdatedAtOnly() {
        repository.searchProjects(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20),
                null,
                ProjectSort.TRENDING,
                ProjectViewCategory.ALL,
                null,
                null
        );

        Document sort = capturedFindQuery().getSortObject();

        assertEquals(1, sort.get("trendingRank"));
        assertEquals(-1, sort.get("trendScore"));
        assertEquals(-1, sort.get("downloads7d"));
        assertEquals(-1, sort.get("downloadCount"));
        assertEquals(-1, sort.get("updatedAt"));
    }

    @Test
    void newestSortUsesCreatedAtInsteadOfUpdatedAtOnly() {
        repository.searchProjects(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20),
                null,
                ProjectSort.NEWEST,
                ProjectViewCategory.ALL,
                null,
                null
        );

        Document sort = capturedFindQuery().getSortObject();

        assertEquals(-1, sort.get("createdAt"));
        assertEquals(-1, sort.get("updatedAt"));
    }

    @Test
    void updatedSortUsesUpdatedAt() {
        repository.searchProjects(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20),
                null,
                ProjectSort.UPDATED,
                ProjectViewCategory.ALL,
                null,
                null
        );

        Document sort = capturedFindQuery().getSortObject();

        assertEquals(-1, sort.get("updatedAt"));
        assertFalse(sort.containsKey("createdAt"));
    }

    @Test
    void downloadsSortWithoutDateUsesDownloadCount() {
        repository.searchProjects(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20),
                null,
                ProjectSort.DOWNLOADS,
                ProjectViewCategory.ALL,
                null,
                null
        );

        Document sort = capturedFindQuery().getSortObject();

        assertEquals(-1, sort.get("downloadCount"));
        assertEquals(-1, sort.get("updatedAt"));
    }

    @Test
    void favoritesSortUsesFavoriteCount() {
        repository.searchProjects(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20),
                null,
                ProjectSort.FAVORITES,
                ProjectViewCategory.ALL,
                null,
                null
        );

        Document sort = capturedFindQuery().getSortObject();

        assertEquals(-1, sort.get("favoriteCount"));
        assertEquals(-1, sort.get("updatedAt"));
    }

    @Test
    void timeWindowDownloadsSortStillUsesWindowedDownloadFields() {
        repository.searchProjects(
                null,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 20),
                null,
                ProjectSort.DOWNLOADS,
                ProjectViewCategory.ALL,
                java.time.LocalDate.now().minusDays(7),
                null
        );

        Document sort = capturedFindQuery().getSortObject();

        assertEquals(-1, sort.get("downloads7d"));
        assertEquals(-1, sort.get("downloadCount"));
    }

    @Test
    void favoritesQueryUsesCatalogSummaryProjection() {
        repository.findFavorites(List.of("project-1"), null, PageRequest.of(0, 10));

        Query query = capturedFindQuery();
        Document fields = query.getFieldsObject();

        assertEquals(1, fields.get("_id"));
        assertEquals(1, fields.get("title"));
        assertEquals(1, fields.get("author"));
        assertEquals(1, fields.get("childProjectIds"));
        assertFalse(fields.containsKey("versions"));
        verify(projectSearchResultDecorator).decorateCatalogResults(org.mockito.ArgumentMatchers.any());
    }

    private Query capturedFindQuery() {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Project.class));
        return queryCaptor.getValue();
    }
}
