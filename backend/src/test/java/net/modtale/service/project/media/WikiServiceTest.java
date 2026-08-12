package net.modtale.service.project.media;

import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WikiServiceTest {

    private ProjectService projectService;
    private WikiUpstreamClient wikiUpstreamClient;
    private ObjectMapper objectMapper;
    private WikiService service;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        wikiUpstreamClient = mock(WikiUpstreamClient.class);
        objectMapper = new ObjectMapper();
        service = new WikiService(projectService, objectMapper, wikiUpstreamClient);
    }

    @Test
    void getWikiProjectThrowsWhenTheProjectDoesNotExist() {
        when(projectService.getProjectPageShellByRouteKey("levelingcore", null)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.getWikiProject("levelingcore", null));
        verifyNoInteractions(wikiUpstreamClient);
    }

    @Test
    void getWikiProjectUsesCachedUpstreamClientPayloads() {
        Project project = new Project();
        project.setId("project-1");
        project.setHmWikiEnabled(true);
        project.setHmWikiSlug("sky-tools");

        when(projectService.getProjectPageShellByRouteKey("levelingcore", null)).thenReturn(project);
        when(wikiUpstreamClient.fetchProjectPayload("sky-tools")).thenReturn("{\"mod\":{\"id\":\"wiki-1\"},\"pages\":[]}");

        service.getWikiProject("levelingcore", null);

        verify(wikiUpstreamClient).fetchProjectPayload("sky-tools");
        verifyNoMoreInteractions(wikiUpstreamClient);
    }

    @Test
    void getWikiPageUsesMetadataIdAndDefaultPageSlug() {
        Project project = new Project();
        project.setId("project-1");
        project.setHmWikiEnabled(true);
        project.setHmWikiSlug("sky-tools");

        when(projectService.getProjectPageShellByRouteKey("levelingcore", null)).thenReturn(project);
        when(wikiUpstreamClient.fetchProjectPayload("sky-tools"))
                .thenReturn("{\"mod\":{\"id\":\"wiki-1\"},\"pages\":[{\"slug\":\"home-1\"}]}");
        when(wikiUpstreamClient.fetchPagePayload("wiki-1", "home-1"))
                .thenReturn("{\"title\":\"Home\"}");

        String result = service.getWikiPage("levelingcore", "", null);

        assertEquals("{\"title\":\"Home\"}", result);
        verify(wikiUpstreamClient).fetchProjectPayload("sky-tools");
        verify(wikiUpstreamClient).fetchPagePayload("wiki-1", "home-1");
        verifyNoMoreInteractions(wikiUpstreamClient);
    }

    @Test
    void getWikiPageBundleCombinesProjectMetadataAndPagePayloads() throws Exception {
        Project project = new Project();
        project.setId("project-1");
        project.setSlug("levelingcore");
        project.setHmWikiEnabled(true);
        project.setHmWikiSlug("sky-tools");

        when(projectService.getProjectPageShellByRouteKey("levelingcore", null)).thenReturn(project);
        when(wikiUpstreamClient.fetchProjectPayload("sky-tools"))
                .thenReturn("{\"mod\":{\"id\":\"wiki-1\"},\"pages\":[{\"slug\":\"home-1\"}]}");
        when(wikiUpstreamClient.fetchPagePayload("wiki-1", "guides/start"))
                .thenReturn("{\"title\":\"Start\"}");

        String result = service.getWikiPageBundle("levelingcore", "guides/start", null);
        JsonNode bundle = objectMapper.readTree(result);

        assertEquals("project-1", bundle.path("project").path("id").asText());
        assertEquals("levelingcore", bundle.path("project").path("slug").asText());
        assertEquals("wiki-1", bundle.path("metadata").path("mod").path("id").asText());
        assertEquals("Start", bundle.path("page").path("title").asText());
        assertEquals("guides/start", bundle.path("pageSlug").asText());
        verify(wikiUpstreamClient).fetchProjectPayload("sky-tools");
        verify(wikiUpstreamClient).fetchPagePayload("wiki-1", "guides/start");
        verifyNoMoreInteractions(wikiUpstreamClient);
    }

    @Test
    void getWikiPageRejectsTraversalSegmentsBeforeFetchingUpstreamContent() {
        assertThrows(IllegalArgumentException.class, () -> service.getWikiPage("project-1", "../admin", null));

        verifyNoInteractions(projectService);
        verifyNoInteractions(wikiUpstreamClient);
    }
}
