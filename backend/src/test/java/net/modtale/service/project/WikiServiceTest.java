package net.modtale.service.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.modtale.config.properties.AppWikiProperties;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.repository.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class WikiServiceTest {

    private ProjectRepository projectRepository;
    private WikiService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        service = spy(new WikiService(projectRepository, new ObjectMapper(), new AppWikiProperties("", "https://wiki.modtale.test/api")));
    }

    @Test
    void getWikiProjectThrowsWhenTheProjectDoesNotExist() {
        when(projectRepository.findById("project-1")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getWikiProject("project-1"));
    }

    @Test
    void getWikiPageRejectsTraversalSegmentsBeforeFetchingUpstreamContent() {
        Project project = new Project();
        project.setId("project-1");
        project.setHmWikiEnabled(true);
        project.setHmWikiSlug("sky-tools");

        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        doReturn("wiki-1").when(service).resolveWikiModId("sky-tools");

        assertThrows(IllegalArgumentException.class, () -> service.getWikiPage("project-1", "../admin"));
    }
}
