package net.modtale.service.worldlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.model.dto.request.worldlist.CreateWorldModListRequest;
import net.modtale.model.dto.worldlist.WorldModListDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.model.worldlist.WorldModList;
import net.modtale.repository.worldlist.WorldModListRepository;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorldModListServiceTest {

    private WorldModListRepository repository;
    private ProjectService projectService;
    private ProjectVersionAccessService versionAccessService;
    private AccessControlService accessControlService;
    private WorldModListArchiveService archiveService;
    private WorldModListService service;

    @BeforeEach
    void setUp() {
        repository = mock(WorldModListRepository.class);
        projectService = mock(ProjectService.class);
        versionAccessService = mock(ProjectVersionAccessService.class);
        accessControlService = mock(AccessControlService.class);
        archiveService = mock(WorldModListArchiveService.class);
        service = new WorldModListService(
                repository,
                projectService,
                versionAccessService,
                accessControlService,
                archiveService,
                new WorldModListMapper(new AppFrontendProperties("https://modtale.test/"))
        );
        when(repository.save(any(WorldModList.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createEnrichesModtaleItemsDedupesAndLeavesExternalItemsListedOnly() {
        User owner = owner();
        Project project = project();
        ProjectVersion version = version("1.2.3", "storage/mod.jar");
        project.setVersions(List.of(version));

        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.isPubliclyReadable(project)).thenReturn(true);
        when(accessControlService.canReadProject(project, owner)).thenReturn(true);
        when(versionAccessService.findByVersionNumber(project, "1.2.3", "0.5.0")).thenReturn(version);

        CreateWorldModListRequest request = new CreateWorldModListRequest(
                "My world share",
                "Cozy World",
                "0.5.0",
                List.of(
                        new CreateWorldModListRequest.Item(
                                "group:mod",
                                "project-1",
                                "",
                                "Local name",
                                "1.2.3",
                                ProjectClassification.PLUGIN,
                                ProjectDependency.Source.MODTALE,
                                "",
                                "",
                                ""
                        ),
                        new CreateWorldModListRequest.Item(
                                "group:mod",
                                "project-1",
                                "",
                                "Duplicate",
                                "1.2.3",
                                ProjectClassification.PLUGIN,
                                ProjectDependency.Source.MODTALE,
                                "",
                                "",
                                ""
                        ),
                        new CreateWorldModListRequest.Item(
                                "local:only",
                                "",
                                "",
                                "Local Only",
                                "0.1.0",
                                ProjectClassification.PLUGIN,
                                ProjectDependency.Source.OTHER,
                                "local:only",
                                "",
                                ""
                        )
                )
        );

        WorldModListDTO dto = service.create(request, owner);

        UUID.fromString(dto.id());
        assertEquals("My world share", dto.title());
        assertEquals("https://modtale.test/lists/" + dto.id(), dto.shareUrl());
        assertEquals("/lists/" + dto.id() + "/download", dto.downloadUrl());
        assertTrue(dto.launcherInstallUrl().startsWith("modtale://install-list?listId=" + dto.id()));
        assertEquals(2, dto.modCount());
        assertEquals(1, dto.downloadableCount());
        assertEquals("Catalog Mod", dto.mods().getFirst().title());
        assertEquals("catalog-mod", dto.mods().getFirst().slug());
        assertEquals("mayuna", dto.mods().getFirst().author());
        assertEquals("A tiny catalog mod.", dto.mods().getFirst().description());
        assertEquals(42, dto.mods().getFirst().downloadCount());
        assertEquals(ProjectDependency.Source.MODTALE, dto.mods().getFirst().source());
        assertEquals("0.1.0", dto.mods().get(1).versionNumber());
        assertEquals("Listed only; Modtale cannot package this external or local file.", dto.mods().get(1).unavailableReason());

        ArgumentCaptor<WorldModList> saved = ArgumentCaptor.forClass(WorldModList.class);
        verify(repository).save(saved.capture());
        assertEquals(dto.id(), saved.getValue().getId());
        assertTrue(saved.getValue().getExpiresAt().isAfter(Instant.now().plusSeconds(29L * 24L * 60L * 60L)));
    }

    @Test
    void viewTouchesListAndExtendsExpiry() {
        Instant oldExpiry = Instant.now().plusSeconds(3600);
        WorldModList list = new WorldModList();
        list.setId("list-1");
        list.setTitle("Shared list");
        list.setWorldName("World");
        list.setCreatedAt(Instant.now().minusSeconds(3600));
        list.setLastViewedAt(Instant.now().minusSeconds(1800));
        list.setExpiresAt(oldExpiry);
        list.setViewCount(2);
        list.setMods(List.of(externalItem()));

        when(repository.findById("list-1")).thenReturn(Optional.of(list));

        WorldModListDTO dto = service.view("list-1");

        assertEquals(3, dto.viewCount());
        assertEquals(0, dto.downloadCount());
        assertTrue(dto.expiresAt().isAfter(oldExpiry));
        assertTrue(dto.lastViewedAt().isAfter(list.getCreatedAt()));
        verify(repository).save(list);
    }

    @Test
    void viewHydratesStoredModtaleItemsWithCurrentProjectMetadata() {
        Instant oldExpiry = Instant.now().plusSeconds(3600);
        WorldModList.Item staleItem = new WorldModList.Item();
        staleItem.setId("item-1");
        staleItem.setProjectId("project-1");
        staleItem.setTitle("Old local title");
        staleItem.setSource(ProjectDependency.Source.OTHER);
        staleItem.setDownloadable(true);

        WorldModList list = new WorldModList();
        list.setId("list-1");
        list.setTitle("Shared list");
        list.setWorldName("World");
        list.setCreatedAt(Instant.now().minusSeconds(3600));
        list.setExpiresAt(oldExpiry);
        list.setMods(List.of(staleItem));

        Project project = project();
        when(repository.findById("list-1")).thenReturn(Optional.of(list));
        when(projectService.getRawProjectById("project-1")).thenReturn(project);
        when(accessControlService.isPubliclyReadable(project)).thenReturn(true);

        WorldModListDTO dto = service.view("list-1");

        WorldModListDTO.Item item = dto.mods().getFirst();
        assertEquals("Catalog Mod", item.title());
        assertEquals("catalog-mod", item.slug());
        assertEquals("author-1", item.authorId());
        assertEquals("mayuna", item.author());
        assertEquals("A tiny catalog mod.", item.description());
        assertEquals("/banners/mod.png", item.bannerUrl());
        assertEquals(42, item.downloadCount());
        assertEquals(7, item.favoriteCount());
        assertEquals(ProjectDependency.Source.MODTALE, item.source());
        verify(repository).save(list);
    }

    @Test
    void viewHydratesStoredItemsBySlugLikeModIdWhenProjectIdIsMissing() {
        WorldModList.Item staleItem = new WorldModList.Item();
        staleItem.setId("item-1");
        staleItem.setModId("AzureDoom:LevelingCore");
        staleItem.setTitle("LevelingCore");
        staleItem.setSource(ProjectDependency.Source.OTHER);
        staleItem.setDownloadable(false);

        WorldModList list = new WorldModList();
        list.setId("list-1");
        list.setTitle("Shared list");
        list.setWorldName("World");
        list.setCreatedAt(Instant.now().minusSeconds(3600));
        list.setExpiresAt(Instant.now().plusSeconds(3600));
        list.setMods(List.of(staleItem));

        Project project = project();
        project.setSlug("leveling-core");
        project.setTitle("LevelingCore");
        when(repository.findById("list-1")).thenReturn(Optional.of(list));
        when(projectService.getRawProjectByRouteKey("leveling-core")).thenReturn(project);
        when(accessControlService.isPubliclyReadable(project)).thenReturn(true);

        WorldModListDTO dto = service.view("list-1");

        WorldModListDTO.Item item = dto.mods().getFirst();
        assertEquals("project-1", item.projectId());
        assertEquals("leveling-core", item.slug());
        assertEquals("LevelingCore", item.title());
        assertEquals("mayuna", item.author());
        assertEquals(42, item.downloadCount());
        assertEquals(7, item.favoriteCount());
        assertEquals(ProjectDependency.Source.MODTALE, item.source());
        verify(repository).save(list);
    }

    @Test
    void downloadTouchesListAndBuildsArchive() throws IOException {
        WorldModList list = new WorldModList();
        list.setId("list-1");
        list.setTitle("Shared list");
        list.setWorldName("A World");
        list.setExpiresAt(Instant.now().plusSeconds(3600));
        list.setMods(List.of(externalItem()));

        when(repository.findById("list-1")).thenReturn(Optional.of(list));
        when(archiveService.generateZip(list)).thenReturn(new byte[]{1, 2, 3});

        WorldModListService.Download download = service.download("list-1");

        assertEquals("A-World-mods.zip", download.filename());
        assertEquals(1, list.getViewCount());
        assertEquals(1, list.getDownloadCount());
        assertEquals(3, download.bytes().length);
    }

    private static User owner() {
        User user = new User();
        user.setId("user-1");
        user.setUsername("willow");
        return user;
    }

    private static Project project() {
        Project project = new Project();
        project.setId("project-1");
        project.setSlug("catalog-mod");
        project.setTitle("Catalog Mod");
        project.setAuthorId("author-1");
        project.setAuthor("mayuna");
        project.setDescription("A tiny catalog mod.");
        project.setImageUrl("/icons/mod.png");
        project.setBannerUrl("/banners/mod.png");
        project.setClassification(ProjectClassification.PLUGIN);
        project.setDownloadCount(42);
        project.setFavoriteCount(7);
        project.setUpdatedAt("2026-06-02T00:00:00Z");
        return project;
    }

    private static ProjectVersion version(String versionNumber, String fileUrl) {
        ProjectVersion version = new ProjectVersion();
        version.setId("version-1");
        version.setVersionNumber(versionNumber);
        version.setGameVersions(List.of("0.5.0"));
        version.setReviewStatus(ProjectVersion.ReviewStatus.APPROVED);
        version.setReleaseDate("2026-06-01");
        version.setFileUrl(fileUrl);
        return version;
    }

    private static WorldModList.Item externalItem() {
        WorldModList.Item item = new WorldModList.Item();
        item.setId("item-1");
        item.setTitle("Local Only");
        item.setSource(ProjectDependency.Source.OTHER);
        item.setDownloadable(false);
        return item;
    }
}
