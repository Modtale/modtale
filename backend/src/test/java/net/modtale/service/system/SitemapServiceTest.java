package net.modtale.service.system;

import java.util.List;
import java.util.Optional;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SitemapServiceTest {

    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private ProjectService projectService;
    private SitemapService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        projectService = mock(ProjectService.class);
        service = new SitemapService(
                projectRepository,
                userRepository,
                projectService,
                new AppFrontendProperties("https://modtale.test")
        );
    }

    @Test
    void generateSitemapIncludesStaticRoutesProjectsAndDeduplicatedAuthorUrls() {
        Project first = project("project-1", "author-id", "willow", "2026-06-01");
        Project second = project("project-2", "author-id", "willow", "2026-06-18T15:30:00");

        when(projectRepository.findAllForSitemap()).thenReturn(List.of(first, second));
        when(projectService.getProjectLink(first)).thenReturn("/plugin/first");
        when(projectService.getProjectLink(second)).thenReturn("/plugin/second");

        String xml = service.generateSitemap();

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("<loc>https://modtale.test/</loc>"));
        assertTrue(xml.contains("<loc>https://modtale.test/plugins</loc>"));
        assertTrue(xml.contains("<loc>https://modtale.test/plugin/first</loc>"));
        assertTrue(xml.contains("<lastmod>2026-06-01</lastmod>"));
        assertTrue(xml.contains("<loc>https://modtale.test/plugin/second</loc>"));
        assertTrue(xml.contains("<lastmod>2026-06-18</lastmod>"));
        assertEquals(1, countOccurrences(xml, "<loc>https://modtale.test/creator/willow</loc>"));
    }

    @Test
    void generateSitemapFallsBackToAuthorLookupAndThenAuthorId() {
        Project withUser = project("project-1", "author-id", null, "2026-06-01");
        Project withMissingUser = project("project-2", "missing-id", null, "2026-06-01");
        User user = new User();
        user.setId("author-id");
        user.setUsername("looked-up");

        when(projectRepository.findAllForSitemap()).thenReturn(List.of(withUser, withMissingUser));
        when(projectService.getProjectLink(withUser)).thenReturn("/plugin/first");
        when(projectService.getProjectLink(withMissingUser)).thenReturn("/plugin/second");
        when(userRepository.findById("author-id")).thenReturn(Optional.of(user));
        when(userRepository.findById("missing-id")).thenReturn(Optional.empty());

        String xml = service.generateSitemap();

        assertTrue(xml.contains("<loc>https://modtale.test/creator/looked-up</loc>"));
        assertTrue(xml.contains("<loc>https://modtale.test/creator/missing-id</loc>"));
    }

    private static Project project(String id, String authorId, String author, String updatedAt) {
        Project project = new Project();
        project.setId(id);
        project.setAuthorId(authorId);
        project.setAuthor(author);
        project.setUpdatedAt(updatedAt);
        return project;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
