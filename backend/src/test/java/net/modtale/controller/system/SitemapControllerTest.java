package net.modtale.controller.system;

import java.util.List;
import java.util.Optional;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.query.ProjectCacheService;
import net.modtale.service.project.query.ProjectRouteService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.project.query.ProjectViewService;
import net.modtale.service.system.SitemapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SitemapControllerTest {

    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private ProjectService projectService;
    private SitemapController controller;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        projectService = new ProjectService(
                mock(ProjectViewService.class),
                mock(ProjectCacheService.class),
                new ProjectRouteService()
        );
        SitemapService sitemapService = new SitemapService(
                projectRepository,
                userRepository,
                projectService,
                new AppFrontendProperties("https://modtale.test")
        );
        controller = new SitemapController(sitemapService);
    }

    @Test
    void generateSitemapIncludesSluggedProjectRoutesAndCreatorPages() {
        Project plugin = project("project-1", "levelingcore", "LevelingCore", ProjectClassification.PLUGIN, "author-1");
        Project modpack = project("project-2", "mega-pack", "Mega Pack", ProjectClassification.MODPACK, "author-2");
        Project world = project("project-3", "sky-world", "Sky World", ProjectClassification.SAVE, "author-3");

        when(userRepository.findById("author-1")).thenReturn(Optional.of(user("author-1", "AzureDoom")));
        when(userRepository.findById("author-2")).thenReturn(Optional.of(user("author-2", "BuilderBee")));
        when(userRepository.findById("author-3")).thenReturn(Optional.of(user("author-3", "CloudCrafter")));
        when(projectRepository.findAllForSitemap()).thenReturn(List.of(plugin, modpack, world));

        String xml = controller.generateSitemap();

        assertTrue(xml.contains("https://modtale.test/mod/levelingcore"));
        assertTrue(xml.contains("https://modtale.test/modpack/mega-pack"));
        assertTrue(xml.contains("https://modtale.test/world/sky-world"));
        assertTrue(xml.contains("https://modtale.test/creator/AzureDoom"));
        assertTrue(xml.contains("https://modtale.test/creator/BuilderBee"));
        assertTrue(xml.contains("https://modtale.test/creator/CloudCrafter"));
        assertTrue(xml.contains("https://modtale.test/api-docs"));
    }

    private static Project project(String id, String slug, String title, ProjectClassification classification, String authorId) {
        Project project = new Project();
        project.setId(id);
        project.setSlug(slug);
        project.setTitle(title);
        project.setClassification(classification);
        project.setAuthorId(authorId);
        project.setUpdatedAt("2026-06-01");
        return project;
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
