package net.modtale.repository.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectViewCategory;
import net.modtale.service.project.ProjectSearchResultDecorator;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectRepositoryImplTest {

    private MongoTemplate mongoTemplate;
    private ProjectRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        repository = new ProjectRepositoryImpl(mongoTemplate, mock(ProjectSearchResultDecorator.class));
        when(mongoTemplate.find(org.mockito.ArgumentMatchers.any(Query.class), eq(Project.class))).thenReturn(List.of());
    }

    @Test
    void popularSortUsesPersistedRankOnly() {
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
        assertFalse(sort.containsKey("popularScore"));
        assertFalse(sort.containsKey("downloadCount"));
        assertFalse(sort.containsKey("favoriteCount"));
        assertTrue(queryJson.contains("popularRank"));
    }

    @Test
    void hiddenGemsUsesPersistedRankOnly() {
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
        assertFalse(sort.containsKey("hiddenGemScore"));
        assertFalse(sort.containsKey("favoriteCount"));
        assertFalse(sort.containsKey("downloadCount"));
        assertTrue(queryJson.contains("hiddenGemRank"));
        assertFalse(queryJson.contains("downloads30d"));
    }

    private Query capturedFindQuery() {
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(Project.class));
        return queryCaptor.getValue();
    }
}
