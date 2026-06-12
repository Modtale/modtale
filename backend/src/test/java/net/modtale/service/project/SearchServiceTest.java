package net.modtale.service.project;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectSort;
import net.modtale.model.project.ProjectViewCategory;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceTest {

    private SearchService searchService;
    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        ProjectSearchResultDecorator projectSearchResultDecorator = new ProjectSearchResultDecorator(userRepository);
        ProjectCatalogSearchService projectCatalogSearchService = new ProjectCatalogSearchService(
                projectRepository,
                projectSearchResultDecorator
        );
        ProjectListingQueryService projectListingQueryService = new ProjectListingQueryService(
                projectRepository,
                userRepository,
                mongoTemplate,
                projectSearchResultDecorator
        );
        searchService = new SearchService(projectCatalogSearchService, projectListingQueryService);
    }

    @Test
    void searchProjectsUsesFavoriteIdsForTheFavoritesView() {
        User currentUser = user("user-1", "viewer");
        currentUser.setLikedModIds(List.of("project-1", "project-2"));
        Page<Project> favorites = new PageImpl<>(List.of(project("project-1", "Sky Tools", ProjectStatus.PUBLISHED)));

        when(projectRepository.findFavorites(eq(List.of("project-1", "project-2")), eq(""), any(Pageable.class)))
                .thenReturn(favorites);

        Page<Project> result = searchService.searchProjects(
                null, null, 0, 12, null, null, null, null, null, ProjectViewCategory.FAVORITES, null, null, currentUser
        );

        assertEquals(favorites, result);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(projectRepository).findFavorites(eq(List.of("project-1", "project-2")), eq(""), pageableCaptor.capture());
        assertEquals(PageRequest.of(0, 12, Sort.by("title")), pageableCaptor.getValue());
        verify(projectRepository, never()).searchProjects(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void searchProjectsAppliesDateFiltersAndSanitizesVersions() {
        User currentUser = user("viewer-1", "viewer");
        User author = user("author-1", "Ada");
        Project project = project("project-1", "Sky Tools", ProjectStatus.PUBLISHED);
        project.setAuthorId("author-1");
        project.setAuthor(null);
        project.setVersions(List.of(version("1.0.0", scanResult(ScanStatus.CLEAN))));
        Page<Project> page = new PageImpl<>(List.of(project));

        when(projectRepository.searchProjects(
                eq("sky"),
                eq(List.of("magic")),
                eq("1.20.1"),
                eq(ProjectClassification.MODPACK),
                eq(10),
                eq(5),
                any(Pageable.class),
                eq("viewer-1"),
                eq(ProjectSort.DOWNLOADS),
                eq(ProjectViewCategory.ALL),
                any(LocalDate.class),
                eq("author-1")
        )).thenReturn(page);
        when(userRepository.findAllById(any(Iterable.class))).thenReturn(List.of(author));

        Page<Project> result = searchService.searchProjects(
                List.of("magic"),
                "sky",
                1,
                25,
                ProjectSort.DOWNLOADS,
                "1.20.1",
                ProjectClassification.MODPACK,
                10,
                5,
                ProjectViewCategory.ALL,
                "30d",
                "author-1",
                currentUser
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        ArgumentCaptor<LocalDate> cutoffCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(projectRepository).searchProjects(
                eq("sky"),
                eq(List.of("magic")),
                eq("1.20.1"),
                eq(ProjectClassification.MODPACK),
                eq(10),
                eq(5),
                pageableCaptor.capture(),
                eq("viewer-1"),
                eq(ProjectSort.DOWNLOADS),
                eq(ProjectViewCategory.ALL),
                cutoffCaptor.capture(),
                eq("author-1")
        );

        assertEquals(PageRequest.of(1, 25), pageableCaptor.getValue());
        assertEquals(LocalDate.now().minusDays(30), cutoffCaptor.getValue());
        assertEquals("Ada", result.getContent().getFirst().getAuthor());
        assertNull(result.getContent().getFirst().getVersions().getFirst().getScanResult());
    }

    @Test
    void searchProjectsRejectsInvalidDateRangesInsteadOfSilentlyIgnoringThem() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> searchService.searchProjects(
                        null,
                        null,
                        0,
                        10,
                        ProjectSort.RELEVANCE,
                        null,
                        null,
                        null,
                        null,
                        ProjectViewCategory.ALL,
                        "not-a-date",
                        null,
                        null
                )
        );

        assertEquals("Date ranges must be 7d, 30d, 90d, 1y, all, or a valid ISO-8601 date.", error.getMessage());
    }

    @Test
    void getCreatorProjectsReturnsEmptyWhenTheCreatorDoesNotExist() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        Page<Project> result = searchService.getCreatorProjects("missing", PageRequest.of(0, 10));

        assertTrue(result.isEmpty());
        verify(projectRepository, never()).findByAuthorIdAndStatusExact(any(), any(), any());
    }

    @Test
    void getCreatorProjectsUsesPublishedOnlyAndDecoratesTheResults() {
        User creator = user("author-1", "Ada");
        Project project = project("project-1", "Sky Tools", ProjectStatus.PUBLISHED);
        project.setVersions(List.of(version("1.0.0", scanResult(ScanStatus.CLEAN))));
        Page<Project> page = new PageImpl<>(List.of(project));

        when(userRepository.findById("author-1")).thenReturn(Optional.of(creator));
        when(projectRepository.findByAuthorIdAndStatusExact("author-1", ProjectStatus.PUBLISHED, PageRequest.of(0, 10)))
                .thenReturn(page);

        Page<Project> result = searchService.getCreatorProjects("author-1", PageRequest.of(0, 10));

        Project decorated = result.getContent().getFirst();
        assertEquals("Ada", decorated.getAuthor());
        assertNull(decorated.getVersions().getFirst().getScanResult());
    }

    @Test
    void getContributedProjectsReadsFromMongoAndEnrichesTheAuthorField() {
        Project project = project("project-1", "Sky Tools", ProjectStatus.PUBLISHED);
        project.setAuthorId("author-1");
        project.setAuthor(null);
        project.setVersions(List.of(version("1.0.0", scanResult(ScanStatus.CLEAN))));

        when(mongoTemplate.count(any(Query.class), eq(Project.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Project.class))).thenReturn(List.of(project));
        when(userRepository.findAllById(any(Iterable.class))).thenReturn(List.of(user("author-1", "Ada")));

        Page<Project> result = searchService.getContributedProjects("contrib-1", PageRequest.of(0, 5));

        assertEquals(1, result.getTotalElements());
        Project contributed = result.getContent().getFirst();
        assertEquals("Ada", contributed.getAuthor());
        assertNull(contributed.getVersions().getFirst().getScanResult());
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private static Project project(String id, String title, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setTitle(title);
        project.setStatus(status);
        return project;
    }

    private static ProjectVersion version(String versionNumber, ScanResult scanResult) {
        ProjectVersion version = new ProjectVersion();
        version.setVersionNumber(versionNumber);
        version.setScanResult(scanResult);
        return version;
    }

    private static ScanResult scanResult(ScanStatus status) {
        ScanResult scanResult = new ScanResult();
        scanResult.setStatus(status);
        return scanResult;
    }
}
