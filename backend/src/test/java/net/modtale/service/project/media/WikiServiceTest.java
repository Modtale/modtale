package net.modtale.service.project.media;

import net.modtale.config.properties.AppWikiProperties;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class WikiServiceTest {

    private ProjectService projectService;
    private WikiService service;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        service = spy(new WikiService(projectService, new ObjectMapper(), new AppWikiProperties("", "https://wiki.modtale.test/api")));
    }

    @Test
    void getWikiProjectThrowsWhenTheProjectDoesNotExist() {
        when(projectService.getProjectById("project-1", null)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.getWikiProject("project-1", null));
    }

    @Test
    void getWikiPageRejectsTraversalSegmentsBeforeFetchingUpstreamContent() {
        Project project = new Project();
        project.setId("project-1");
        project.setHmWikiEnabled(true);
        project.setHmWikiSlug("sky-tools");

        when(projectService.getProjectById("project-1", null)).thenReturn(project);
        doReturn("wiki-1").when(service).resolveWikiModId("sky-tools");

        assertThrows(IllegalArgumentException.class, () -> service.getWikiPage("project-1", "../admin", null));
    }
}
