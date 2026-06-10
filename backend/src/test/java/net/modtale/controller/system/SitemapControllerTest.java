package net.modtale.controller.system;

import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.model.project.Project;
import net.modtale.service.project.ProjectService;
import net.modtale.service.project.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SitemapControllerTest {

    private SearchService searchService;
    private ProjectService projectService;
    private SitemapController controller;

    @BeforeEach
    void setUp() {
        searchService = mock(SearchService.class);
        projectService = mock(ProjectService.class);
        controller = new SitemapController(searchService, projectService, new AppFrontendProperties("https://modtale.test"));
    }

    @Test
    void generateSitemapIncludesPublishedProjectRoutesAndCreatorPages() {
        Project project = new Project();
        project.setId("project-1");
        project.setAuthorId("author-1");
        project.setUpdatedAt("2026-06-01");

        when(searchService.getPublishedProjects()).thenReturn(List.of(project));
        when(projectService.getProjectLink(project)).thenReturn("/mod/sky-tools~project-1");

        String xml = controller.generateSitemap();

        assertTrue(xml.contains("https://modtale.test/mod/sky-tools~project-1"));
        assertTrue(xml.contains("https://modtale.test/creator/author-1"));
        assertTrue(xml.contains("https://modtale.test/api-docs"));
    }
}
