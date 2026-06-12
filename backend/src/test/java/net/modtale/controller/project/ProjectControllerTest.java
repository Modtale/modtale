package net.modtale.controller.project;

import net.modtale.exception.ResourceNotFoundException;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.dto.project.ProjectDTO;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.dto.request.project.UpdateProjectRequest;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ProjectViewCategory;
import net.modtale.model.user.User;
import net.modtale.service.project.GameVersionService;
import net.modtale.service.project.LifecycleService;
import net.modtale.service.project.MetadataService;
import net.modtale.service.project.ProjectRetentionService;
import net.modtale.service.project.ProjectResponseCacheService;
import net.modtale.service.project.ProjectService;
import net.modtale.service.project.SearchService;
import net.modtale.service.project.ValidationService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.security.PermissionProjectLookupService;
import net.modtale.service.user.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectControllerTest {

    private ProjectController controller;
    private ProjectService projectService;
    private SearchService searchService;
    private LifecycleService lifecycleService;
    private ProjectRetentionService projectRetentionService;
    private MetadataService metadataService;
    private ValidationService validationService;
    private GameVersionService gameVersionService;
    private ProjectResponseCacheService projectResponseCacheService;
    private AccessControlService accessControlService;
    private AccountService accountService;
    private PermissionProjectLookupService permissionProjectLookupService;

    @BeforeEach
    void setUp() {
        projectService = mock(ProjectService.class);
        searchService = mock(SearchService.class);
        lifecycleService = mock(LifecycleService.class);
        projectRetentionService = mock(ProjectRetentionService.class);
        metadataService = mock(MetadataService.class);
        validationService = mock(ValidationService.class);
        gameVersionService = mock(GameVersionService.class);
        projectResponseCacheService = mock(ProjectResponseCacheService.class);
        accessControlService = mock(AccessControlService.class);
        accountService = mock(AccountService.class);
        permissionProjectLookupService = mock(PermissionProjectLookupService.class);

        controller = new ProjectController(
                projectService,
                searchService,
                lifecycleService,
                projectRetentionService,
                metadataService,
                validationService,
                gameVersionService,
                projectResponseCacheService,
                accessControlService,
                accountService,
                permissionProjectLookupService
        );
    }

    @Test
    void getProjectsUsesAuthorIdAndDisablesCachingForFavorites() {
        User currentUser = user("user-1", "ada");
        Page<Project> page = new PageImpl<>(List.of(project("project-1", "Sky Tools", ProjectStatus.PUBLISHED)));
        when(accountService.getCurrentUser((Authentication) null)).thenReturn(currentUser);
        when(searchService.searchProjects(
                eq(List.of("adventure", "tools")),
                eq("sky"),
                eq(2),
                eq(25),
                eq(ProjectSort.POPULAR),
                eq("1.0.0"),
                eq(ProjectClassification.MODPACK),
                eq(10),
                eq(5),
                eq(ProjectViewCategory.FAVORITES),
                eq("30d"),
                eq("Ada"),
                eq(currentUser)
        )).thenReturn(page);

        var response = controller.getProjects(
                "adventure, tools",
                "sky",
                2,
                25,
                "popular",
                "1.0.0",
                "MODPACK",
                10,
                5,
                "Favorites",
                "30d",
                "Ada",
                null
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("no-cache", response.getHeaders().getCacheControl());
        ProjectSummaryDTO dto = response.getBody().getContent().getFirst();
        assertEquals("project-1", dto.id());
        assertEquals("Sky Tools", dto.title());
        verify(accountService).getCurrentUser((Authentication) null);
    }

    @Test
    void getProjectsRemovesPersonalCategoriesForApiKeyRequests() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "api",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_API"))
        );
        Page<ProjectSummaryDTO> page = new PageImpl<>(List.of(ProjectMapper.toSummaryDTO(project("project-2", "Cloud Pack", ProjectStatus.PUBLISHED))));

        when(projectResponseCacheService.searchPublicProjectSummaries(
                isNull(),
                isNull(),
                eq(0),
                eq(10),
                eq(ProjectSort.RELEVANCE),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(ProjectViewCategory.ALL),
                isNull(),
                eq("creator-name")
        )).thenReturn(page);

        var response = controller.getProjects(
                null,
                null,
                0,
                10,
                "relevance",
                null,
                null,
                null,
                null,
                "Your Projects",
                null,
                "creator-name",
                authentication
        );

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getCacheControl().contains("max-age=3600"));
        verify(accountService, never()).getCurrentUser(authentication);
        verify(projectResponseCacheService).searchPublicProjectSummaries(
                isNull(),
                isNull(),
                eq(0),
                eq(10),
                eq(ProjectSort.RELEVANCE),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(ProjectViewCategory.ALL),
                isNull(),
                eq("creator-name")
        );
    }

    @Test
    void getProjectsDoesNotResolveCurrentUserForPublicCatalogViews() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user("user-1", "ada"),
                null,
                List.of()
        );
        Page<ProjectSummaryDTO> page = new PageImpl<>(List.of(ProjectMapper.toSummaryDTO(project("project-3", "Sorted Tags", ProjectStatus.PUBLISHED))));

        when(projectResponseCacheService.searchPublicProjectSummaries(
                eq(List.of("adventure", "tools")),
                eq(""),
                eq(0),
                eq(12),
                eq(ProjectSort.RELEVANCE),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(ProjectViewCategory.ALL),
                eq("all"),
                isNull()
        )).thenReturn(page);

        var response = controller.getProjects(
                "tools, adventure, tools",
                "",
                0,
                12,
                "relevance",
                null,
                null,
                null,
                null,
                "all",
                "all",
                null,
                authentication
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("project-3", response.getBody().getContent().getFirst().id());
        verify(accountService, never()).getCurrentUser(authentication);
        verify(projectResponseCacheService).searchPublicProjectSummaries(
                eq(List.of("adventure", "tools")),
                eq(""),
                eq(0),
                eq(12),
                eq(ProjectSort.RELEVANCE),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(ProjectViewCategory.ALL),
                eq("all"),
                isNull()
        );
    }

    @Test
    void getProjectThrowsWhenViewerCannotResolveVisibleProject() {
        User currentUser = user("user-1", "ada");
        when(accountService.getCurrentUser((Authentication) null)).thenReturn(currentUser);
        when(permissionProjectLookupService.findProject("project-1")).thenReturn(project("project-1", "Sky Tools", ProjectStatus.PUBLISHED));
        when(projectService.getProjectPageByRouteKey("project-1", currentUser)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> controller.getProject("project-1", null));
    }

    @Test
    void getProjectUsesCachedPublicProjectForSignedInReaders() {
        User currentUser = user("user-1", "ada");
        Project snapshot = project("project-1", "Sky Tools", ProjectStatus.PUBLISHED);
        ProjectDTO cachedProject = ProjectMapper.toDTO(project("project-1", "Sky Tools", ProjectStatus.PUBLISHED), false);

        when(accountService.getCurrentUser((Authentication) null)).thenReturn(currentUser);
        when(permissionProjectLookupService.findProject("project-1")).thenReturn(snapshot);
        when(accessControlService.isAdmin(currentUser)).thenReturn(false);
        when(accessControlService.hasEditPermission(snapshot, currentUser)).thenReturn(false);
        when(accessControlService.isPubliclyReadable(snapshot)).thenReturn(true);
        when(projectResponseCacheService.getPublicProjectDtoByRouteKey("project-1")).thenReturn(cachedProject);

        var response = controller.getProject("project-1", null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("project-1", response.getBody().getId());
        assertTrue(response.getHeaders().getCacheControl().contains("max-age=300"));
        assertTrue(response.getHeaders().getCacheControl().contains("public"));
        verify(projectResponseCacheService).getPublicProjectDtoByRouteKey("project-1");
        verify(projectService, never()).getProjectPageByRouteKey("project-1", currentUser);
    }

    @Test
    void getProjectMetaUsesCachedPublicMetaForSignedInReaders() {
        User currentUser = user("user-1", "ada");
        Project snapshot = project("project-1", "Sky Tools", ProjectStatus.PUBLISHED);

        when(accountService.getCurrentUser((Authentication) null)).thenReturn(currentUser);
        when(permissionProjectLookupService.findProject("project-1")).thenReturn(snapshot);
        when(accessControlService.isAdmin(currentUser)).thenReturn(false);
        when(accessControlService.hasEditPermission(snapshot, currentUser)).thenReturn(false);
        when(accessControlService.isPubliclyReadable(snapshot)).thenReturn(true);
        when(projectResponseCacheService.getPublicProjectMetaByRouteKey("project-1"))
                .thenReturn(new net.modtale.model.dto.project.ProjectMetaDTO("Sky Tools", "Fast path", "/icon.png", "Ada", ProjectClassification.MODPACK, 10, "", "project-1"));

        var response = controller.getProjectMeta("project-1", null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Sky Tools", response.getBody().title());
        assertTrue(response.getHeaders().getCacheControl().contains("max-age=300"));
        assertTrue(response.getHeaders().getCacheControl().contains("public"));
        verify(projectResponseCacheService).getPublicProjectMetaByRouteKey("project-1");
        verify(projectService, never()).getProjectByRouteKey("project-1", currentUser);
    }

    @Test
    void getProjectDecoratesReturnedDtoWithEditFlagsForCurrentUser() {
        Project project = project("project-1", "Sky Tools", ProjectStatus.PUBLISHED);
        ProjectVersion version = new ProjectVersion();
        version.setId("version-1");
        version.setVersionNumber("1.0.0");
        version.setChangelog("Huge changelog body that should not ride on page load.");
        project.setVersions(List.of(version));
        User currentUser = user("user-1", "ada");

        when(accountService.getCurrentUser((Authentication) null)).thenReturn(currentUser);
        when(permissionProjectLookupService.findProject("project-1")).thenReturn(project);
        when(accessControlService.isAdmin(currentUser)).thenReturn(false);
        when(accessControlService.isPubliclyReadable(project)).thenReturn(true);
        when(projectService.getProjectPageByRouteKey("project-1", currentUser)).thenReturn(project);
        when(accessControlService.hasEditPermission(project, currentUser)).thenReturn(true);
        when(accessControlService.isOwner(project, currentUser)).thenReturn(false);

        var response = controller.getProject("project-1", null);

        assertEquals(200, response.getStatusCode().value());
        ProjectDTO dto = assertInstanceOf(ProjectDTO.class, response.getBody());
        assertEquals("project-1", dto.getId());
        assertTrue(dto.isCanEdit());
        assertFalse(dto.isOwner());
        assertEquals(1, dto.getVersions().size());
        assertEquals("version-1", dto.getVersions().getFirst().getId());
        assertEquals(null, dto.getVersions().getFirst().getChangelog());
        assertEquals("no-cache", response.getHeaders().getCacheControl());
    }

    @Test
    void getProjectCachesAnonymousPublishedProjectDetails() {
        Project project = project("project-1", "Sky Tools", ProjectStatus.PUBLISHED);

        when(accountService.getCurrentUser((Authentication) null)).thenReturn(null);
        when(projectResponseCacheService.getPublicProjectDtoByRouteKey("project-1")).thenReturn(ProjectMapper.toDTO(project, false));

        var response = controller.getProject("project-1", null);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getCacheControl().contains("max-age=300"));
        assertTrue(response.getHeaders().getCacheControl().contains("public"));
    }

    @Test
    void getProjectUsesCanonicalSlugRoutesForAnonymousProjects() {
        Project project = project("project-1", "LevelingCore", ProjectStatus.PUBLISHED);
        project.setSlug("levelingcore");

        when(accountService.getCurrentUser((Authentication) null)).thenReturn(null);
        when(projectResponseCacheService.getPublicProjectDtoByRouteKey("levelingcore")).thenReturn(ProjectMapper.toDTO(project, false));

        var response = controller.getProject("levelingcore", null);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getCacheControl().contains("max-age=300"));
        assertTrue(response.getHeaders().getCacheControl().contains("public"));
        ProjectDTO dto = assertInstanceOf(ProjectDTO.class, response.getBody());
        assertEquals("levelingcore", dto.getSlug());
    }

    @Test
    void updateProjectPreservesExistingBooleanFlagsWhenRequestOmitsThem() {
        User currentUser = user("user-1", "ada");
        Project existing = project("project-1", "Existing", ProjectStatus.DRAFT);
        existing.setAllowModpacks(false);
        existing.setAllowComments(false);
        existing.setHmWikiEnabled(true);
        existing.setHmWikiSlug("existing-wiki");

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setTitle("Updated Title");
        request.setDescription("Short summary");
        request.setAbout("Longer details");
        request.setTags(List.of("Adventure"));
        request.setLinks(Map.of("discord", "https://discord.gg/modtale"));

        when(accountService.requireCurrentUser(null, "editing project metadata")).thenReturn(currentUser);
        when(projectService.getRawProjectById("project-1")).thenReturn(existing);
        doNothing().when(metadataService).updateMetadata(eq("project-1"), org.mockito.ArgumentMatchers.any(Project.class), eq(currentUser));

        var response = controller.updateProject("project-1", request, null);

        assertEquals(200, response.getStatusCode().value());

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(metadataService).updateMetadata(eq("project-1"), captor.capture(), eq(currentUser));

        Project updated = captor.getValue();
        assertEquals("Updated Title", updated.getTitle());
        assertEquals("Short summary", updated.getDescription());
        assertEquals("Longer details", updated.getAbout());
        assertEquals(List.of("Adventure"), updated.getTags());
        assertFalse(updated.isAllowModpacks());
        assertFalse(updated.isAllowComments());
        assertTrue(updated.isHmWikiEnabled());
        assertEquals("existing-wiki", updated.getHmWikiSlug());
    }

    @Test
    void deleteProjectDelegatesToProjectRetentionService() {
        User currentUser = user("user-1", "ada");
        when(accountService.requireCurrentUser(null, "deleting a project")).thenReturn(currentUser);

        var response = controller.deleteProject("project-1", null);

        assertEquals(200, response.getStatusCode().value());
        verify(projectRetentionService).softDeleteProject("project-1", currentUser);
    }

    private static Project project(String id, String title, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setTitle(title);
        project.setSlug(id + "-slug");
        project.setStatus(status);
        project.setClassification(ProjectClassification.MODPACK);
        project.setAuthor("Ada");
        project.setAuthorId("author-1");
        return project;
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
