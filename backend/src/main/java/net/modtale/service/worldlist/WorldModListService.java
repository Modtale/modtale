package net.modtale.service.worldlist;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.dto.request.worldlist.CreateWorldModListRequest;
import net.modtale.model.dto.worldlist.WorldModListDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.model.worldlist.WorldModList;
import net.modtale.repository.worldlist.WorldModListRepository;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.AccessControlService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WorldModListService {

    private static final Duration EXPIRY_WINDOW = Duration.ofDays(30);

    private final WorldModListRepository repository;
    private final ProjectService projectService;
    private final ProjectVersionAccessService versionAccessService;
    private final AccessControlService accessControlService;
    private final WorldModListArchiveService archiveService;
    private final WorldModListMapper mapper;

    public WorldModListService(
            WorldModListRepository repository,
            ProjectService projectService,
            ProjectVersionAccessService versionAccessService,
            AccessControlService accessControlService,
            WorldModListArchiveService archiveService,
            WorldModListMapper mapper
    ) {
        this.repository = repository;
        this.projectService = projectService;
        this.versionAccessService = versionAccessService;
        this.accessControlService = accessControlService;
        this.archiveService = archiveService;
        this.mapper = mapper;
    }

    public WorldModListDTO create(CreateWorldModListRequest request, User owner) {
        if (owner == null) {
            throw new InvalidProjectRequestException("Sign in before sharing a world mod list.");
        }
        List<CreateWorldModListRequest.Item> requestedMods = request.mods() == null ? List.of() : request.mods();
        Map<String, WorldModList.Item> items = new LinkedHashMap<>();
        for (CreateWorldModListRequest.Item requested : requestedMods) {
            WorldModList.Item item = normalizeItem(requested, request.gameVersion(), owner);
            String key = itemKey(item);
            if (!key.isBlank()) {
                items.putIfAbsent(key, item);
            }
        }
        if (items.isEmpty()) {
            throw new InvalidProjectRequestException("Pick at least one enabled mod before sharing this list.");
        }

        Instant now = Instant.now();
        WorldModList list = new WorldModList();
        list.setOwnerId(owner.getId());
        list.setOwnerUsername(owner.getUsername());
        list.setTitle(firstText(request.title(), request.worldName(), "Shared world mods"));
        list.setWorldName(firstText(request.worldName(), "Hytale world"));
        list.setGameVersion(value(request.gameVersion()));
        list.setCreatedAt(now);
        list.setLastViewedAt(now);
        list.setExpiresAt(now.plus(EXPIRY_WINDOW));
        list.setMods(items.values().stream().toList());
        return mapper.toDTO(repository.save(list));
    }

    public WorldModListDTO view(String id) {
        return mapper.toDTO(touch(findActive(id), true, false));
    }

    public WorldModListDTO metadataForInstall(String id) {
        return mapper.toDTO(touch(findActive(id), true, false));
    }

    public Download download(String id) throws IOException {
        WorldModList list = touch(findActive(id), true, true);
        return new Download(filename(list), archiveService.generateZip(list));
    }

    @Scheduled(cron = "${app.world-lists.cleanup-cron:0 20 3 * * ?}")
    public void cleanupExpiredLists() {
        repository.deleteByExpiresAtBefore(Instant.now());
    }

    private WorldModList touch(WorldModList list, boolean view, boolean download) {
        refreshPublicProjectMetadata(list);
        Instant now = Instant.now();
        list.setLastViewedAt(now);
        list.setExpiresAt(now.plus(EXPIRY_WINDOW));
        if (view) {
            list.setViewCount(list.getViewCount() + 1);
        }
        if (download) {
            list.setDownloadCount(list.getDownloadCount() + 1);
        }
        return repository.save(list);
    }

    private WorldModList findActive(String id) {
        WorldModList list = repository.findById(id == null ? "" : id.trim()).orElse(null);
        if (list == null || isExpired(list)) {
            throw new ResourceNotFoundException("That shared mod list is gone or has expired.");
        }
        return list;
    }

    private boolean isExpired(WorldModList list) {
        return list.getExpiresAt() != null && Instant.now().isAfter(list.getExpiresAt());
    }

    private void refreshPublicProjectMetadata(WorldModList list) {
        if (list == null || list.getMods() == null || list.getMods().isEmpty()) {
            return;
        }
        for (WorldModList.Item item : list.getMods()) {
            Project project = projectFor(item);
            if (project == null || !accessControlService.isPubliclyReadable(project)) {
                continue;
            }
            applyProjectMetadata(item, project);
            item.setSource(ProjectDependency.Source.MODTALE);
        }
    }

    private WorldModList.Item normalizeItem(CreateWorldModListRequest.Item requested, String gameVersion, User owner) {
        WorldModList.Item item = new WorldModList.Item();
        if (requested == null) {
            item.setUnavailableReason("Empty list item.");
            return item;
        }

        item.setModId(value(requested.modId()));
        item.setProjectId(value(requested.projectId()));
        item.setSlug(value(requested.slug()));
        item.setTitle(firstText(requested.title(), requested.modId(), requested.projectId(), "Unknown mod"));
        item.setVersionNumber(value(requested.versionNumber()));
        item.setClassification(requested.classification());
        item.setSource(requested.source() == null ? sourceFor(requested) : requested.source());
        item.setExternalId(value(requested.externalId()));
        item.setExternalUrl(value(requested.externalUrl()));
        item.setIcon(value(requested.icon()));

        if (item.getSource() == ProjectDependency.Source.MODTALE || !item.getProjectId().isBlank()) {
            enrichModtaleItem(item, gameVersion, owner);
        } else {
            item.setDownloadable(false);
            item.setUnavailableReason("Listed only; Modtale cannot package this external or local file.");
        }
        return item;
    }

    private void enrichModtaleItem(WorldModList.Item item, String gameVersion, User owner) {
        Project project = projectFor(item);
        if (project == null || !accessControlService.isPubliclyReadable(project) || !accessControlService.canReadProject(project, owner)) {
            item.setDownloadable(false);
            item.setUnavailableReason("Project is not public on Modtale.");
            return;
        }

        ProjectVersion version = versionFor(project, item.getVersionNumber(), gameVersion);
        applyProjectMetadata(item, project);
        item.setSource(ProjectDependency.Source.MODTALE);
        item.setVersionNumber(version == null ? item.getVersionNumber() : version.getVersionNumber());
        item.setFileUrl(version == null ? "" : value(version.getFileUrl()));
        item.setDownloadable(version != null && version.getFileUrl() != null && !version.getFileUrl().isBlank());
        if (!item.isDownloadable()) {
            item.setUnavailableReason("No downloadable public version could be resolved.");
        }
    }

    private void applyProjectMetadata(WorldModList.Item item, Project project) {
        item.setProjectId(project.getId());
        item.setSlug(firstText(project.getSlug(), project.getId()));
        item.setTitle(firstText(project.getTitle(), item.getTitle()));
        item.setAuthorId(value(project.getAuthorId()));
        item.setAuthor(value(project.getAuthor()));
        item.setDescription(value(project.getDescription()));
        item.setClassification(project.getClassification());
        item.setIcon(firstText(project.getImageUrl(), item.getIcon()));
        item.setBannerUrl(value(project.getBannerUrl()));
        item.setDownloadCount(project.getDownloadCount());
        item.setFavoriteCount(project.getFavoriteCount());
        item.setUpdatedAt(value(project.getUpdatedAt()));
    }

    private Project projectFor(WorldModList.Item item) {
        if (item == null) {
            return null;
        }
        String projectId = value(item.getProjectId());
        if (!projectId.isBlank()) {
            Project project = projectService.getRawProjectById(projectId);
            if (project != null) {
                return project;
            }
        }
        String slug = value(item.getSlug());
        if (!slug.isBlank()) {
            Project project = projectService.getRawProjectByRouteKey(slug);
            if (project != null) {
                return project;
            }
        }
        for (String candidate : routeKeyCandidates(item)) {
            Project project = projectService.getRawProjectByRouteKey(candidate);
            if (project != null) {
                return project;
            }
        }
        return null;
    }

    private Set<String> routeKeyCandidates(WorldModList.Item item) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addRouteKeyCandidates(candidates, item.getExternalId());
        addRouteKeyCandidates(candidates, item.getModId());
        addRouteKeyCandidates(candidates, item.getTitle());
        return candidates;
    }

    private void addRouteKeyCandidates(Set<String> candidates, String value) {
        String normalized = value(value);
        if (normalized.isBlank()) {
            return;
        }
        candidates.add(normalized);
        addSlugCandidates(candidates, normalized);

        int separator = normalized.lastIndexOf(':');
        if (separator >= 0 && separator < normalized.length() - 1) {
            String suffix = normalized.substring(separator + 1).trim();
            if (!suffix.isBlank()) {
                candidates.add(suffix);
                addSlugCandidates(candidates, suffix);
            }
        }
    }

    private void addSlugCandidates(Set<String> candidates, String value) {
        String camelSeparated = value.replaceAll("([a-z0-9])([A-Z])", "$1-$2");
        String slug = camelSeparated.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (!slug.isBlank()) {
            candidates.add(slug);
        }

        String compact = value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        if (!compact.isBlank()) {
            candidates.add(compact);
        }
    }

    private ProjectVersion versionFor(Project project, String versionNumber, String gameVersion) {
        ProjectVersion version = null;
        if (versionNumber != null && !versionNumber.isBlank()) {
            version = versionAccessService.findByVersionNumber(project, versionNumber, gameVersion);
        }
        if (isApproved(version)) {
            return version;
        }
        return project.getVersions() == null ? null : project.getVersions().stream()
                .filter(this::isApproved)
                .filter(candidate -> supportsGameVersion(candidate, gameVersion))
                .max(Comparator.comparing(ProjectVersion::getReleaseDate, Comparator.nullsLast(String::compareTo)))
                .orElse(null);
    }

    private boolean isApproved(ProjectVersion version) {
        return version != null && version.getReviewStatus() == ProjectVersion.ReviewStatus.APPROVED;
    }

    private boolean supportsGameVersion(ProjectVersion version, String gameVersion) {
        return gameVersion == null
                || gameVersion.isBlank()
                || version.getGameVersions() == null
                || version.getGameVersions().stream().anyMatch(gameVersion::equalsIgnoreCase);
    }

    private ProjectDependency.Source sourceFor(CreateWorldModListRequest.Item item) {
        return item.projectId() == null || item.projectId().isBlank()
                ? ProjectDependency.Source.OTHER
                : ProjectDependency.Source.MODTALE;
    }

    private String itemKey(WorldModList.Item item) {
        if (!item.getProjectId().isBlank()) {
            return item.getSource() + ":" + item.getProjectId();
        }
        if (!item.getExternalId().isBlank()) {
            return item.getSource() + ":" + item.getExternalId();
        }
        return firstText(item.getModId(), item.getTitle());
    }

    private String filename(WorldModList list) {
        String base = firstText(list.getWorldName(), list.getTitle(), "world-mod-list")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return (base.isBlank() ? "world-mod-list" : base) + "-mods.zip";
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    public record Download(String filename, byte[] bytes) {
    }
}
