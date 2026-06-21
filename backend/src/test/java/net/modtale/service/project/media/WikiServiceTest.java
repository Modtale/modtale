package net.modtale.service.project.media;

import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WikiServiceTest {

    private ProjectService projectService;
    private WikiUpstreamClient wikiUpstreamClient;
    private WikiService service;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        wikiUpstreamClient = mock(WikiUpstreamClient.class);
        service = new WikiService(projectService, new ObjectMapper(), wikiUpstreamClient);
    }

    @Test
    void getWikiProjectThrowsWhenTheProjectDoesNotExist() {
        when(projectService.getProjectByRouteKey("levelingcore", null)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.getWikiProject("levelingcore", null));
        verifyNoInteractions(wikiUpstreamClient);
    }

    @Test
    void getWikiProjectUsesCachedUpstreamClientPayloads() {
        Project project = new Project();
        project.setId("project-1");
        project.setHmWikiEnabled(true);
        project.setHmWikiSlug("sky-tools");

        when(projectService.getProjectByRouteKey("levelingcore", null)).thenReturn(project);
        when(wikiUpstreamClient.resolveWikiModId("sky-tools")).thenReturn("wiki-1");
        when(wikiUpstreamClient.fetchProjectPayload("wiki-1")).thenReturn("{\"index\":{\"slug\":\"home-1\"},\"pages\":[]}");

        service.getWikiProject("levelingcore", null);

        verify(wikiUpstreamClient).resolveWikiModId("sky-tools");
        verify(wikiUpstreamClient).fetchProjectPayload("wiki-1");
        verifyNoMoreInteractions(wikiUpstreamClient);
    }

    @Test
    void getWikiPageRejectsTraversalSegmentsBeforeFetchingUpstreamContent() {
        assertThrows(IllegalArgumentException.class, () -> service.getWikiPage("project-1", "../admin", null));

        verifyNoInteractions(projectService);
        verifyNoInteractions(wikiUpstreamClient);
    }
}
