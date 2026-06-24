package net.modtale.service.system;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.model.jam.Modjam;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.ModjamService;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SitemapServiceTest {

    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private ProjectService projectService;
    private ModjamService modjamService;
    private SitemapService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        projectService = mock(ProjectService.class);
        modjamService = mock(ModjamService.class);
        service = new SitemapService(
                projectRepository,
                userRepository,
                projectService,
                modjamService,
                new AppFrontendProperties("https://modtale.test")
        );
        when(modjamService.getAllJams()).thenReturn(List.of());
    }

    @Test
    void generateSitemapIncludesStaticRoutesProjectsAndDeduplicatedAuthorUrls() {
        Project first = project("project-1", "author-id", "willow", "2026-06-01");
        Project second = project("project-2", "author-id", "willow", "not-a-date");

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

    @Test
    void generateSitemapIncludesVisibleJamsAndSkipsDrafts() {
        Modjam active = jam("active-jam", "ACTIVE", "host-one", "2026-06-15T12:00:00Z");
        Modjam completed = jam("done-jam", "COMPLETED", "host-one", "2026-06-10T12:00:00Z");
        Modjam draft = jam("draft-jam", "DRAFT", "host-two", "2026-06-20T12:00:00Z");

        when(projectRepository.findAllForSitemap()).thenReturn(List.of());
        when(modjamService.getAllJams()).thenReturn(List.of(active, completed, draft));

        String xml = service.generateSitemap();

        assertTrue(xml.contains("<loc>https://modtale.test/jams</loc>"));
        assertTrue(xml.contains("<loc>https://modtale.test/jam/active-jam</loc>"));
        assertTrue(xml.contains("<lastmod>2026-06-15</lastmod>"));
        assertTrue(xml.contains("<loc>https://modtale.test/jam/done-jam</loc>"));
        assertFalse(xml.contains("draft-jam"));
        assertEquals(1, countOccurrences(xml, "<loc>https://modtale.test/creator/host-one</loc>"));
    }

    private static Project project(String id, String authorId, String author, String updatedAt) {
        Project project = new Project();
        project.setId(id);
        project.setAuthorId(authorId);
        project.setAuthor(author);
        project.setUpdatedAt(updatedAt);
        return project;
    }

    private static Modjam jam(String slug, String status, String hostName, String updatedAt) {
        Modjam jam = new Modjam();
        jam.setSlug(slug);
        jam.setStatus(status);
        jam.setHostName(hostName);
        jam.setUpdatedAt(Instant.parse(updatedAt));
        return jam;
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
