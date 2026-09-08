package net.modtale.launcher.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.modtale.launcher.model.project.DownloadUrlResponse;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectPage;
import net.modtale.launcher.model.project.ProjectMeta;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.model.project.ProjectVersion;

/** Direct, keyless CurseForge catalog access through nyoCF. */
final class NyoCfClient {

    private static final URI DEFAULT_BASE_URI = URI.create("https://nyocf.junyo.dev");
    private static final String CURSEFORGE_SITE = "https://www.curseforge.com";
    private static final long HYTALE_GAME_ID = 70216;
    private static final List<String> PROJECT_CLASSES = List.of(
            "mods", "prefabs", "worlds", "bootstrap", "translations");
    private final ModtaleApiTransport transport;
    private final URI baseUri;

    NyoCfClient(HttpClient httpClient) {
        this(httpClient, DEFAULT_BASE_URI);
    }

    NyoCfClient(HttpClient httpClient, URI baseUri) {
        this(httpClient, baseUri, new ApiResponseCache());
    }

    NyoCfClient(HttpClient httpClient, ApiResponseCache responseCache) {
        this(httpClient, DEFAULT_BASE_URI, responseCache);
    }

    NyoCfClient(HttpClient httpClient, URI baseUri, ApiResponseCache responseCache) {
        this.transport = new ModtaleApiTransport(httpClient, responseCache);
        this.baseUri = baseUri;
    }

    ProjectPage search(ProjectSearchQuery query) {
        int page = Math.max(0, query.page());
        int size = Math.max(1, Math.min(50, query.size()));
        String projectClass = projectClass(query.classification());
        if (projectClass == null) return searchAll(query, page, size);
        return searchClass(query, projectClass, page, size);
    }

    private ProjectPage searchClass(ProjectSearchQuery query, String projectClass, int page, int size) {
        String path = "/api/v1/hytale/" + projectClass + "/search?q=" + encode(value(query.search()))
                + "&limit=" + size + "&offset=" + (page * size) + "&include_files=true";
        JsonNode envelope = get(path);
        List<ProjectSummary> projects = new ArrayList<>();
        for (JsonNode item : envelope.path("data")) {
            ProjectSummary project = summary(item, query.gameVersion(), projectClass);
            if (project != null) projects.add(project);
        }
        sort(projects, query.sort());
        long total = Math.max(projects.size(), envelope.path("pagination").path("total").asLong(projects.size()));
        int pages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new ProjectPage(List.copyOf(projects), pages, total, page, page + 1 >= pages);
    }

    private ProjectPage searchAll(ProjectSearchQuery query, int page, int size) {
        List<CompletableFuture<ProjectPage>> searches = PROJECT_CLASSES.stream()
                .map(projectClass -> CompletableFuture.supplyAsync(
                        () -> searchClass(query, projectClass, page, size)))
                .toList();
        List<ProjectSummary> projects = new ArrayList<>();
        long total = 0;
        for (CompletableFuture<ProjectPage> search : searches) {
            ProjectPage result = search.join();
            projects.addAll(result.content());
            total += result.totalElements();
        }
        sort(projects, query.sort());
        List<ProjectSummary> pageContent = projects.stream().limit(size).toList();
        int pages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new ProjectPage(pageContent, pages, total, page, page + 1 >= pages);
    }

    private String projectClass(String classification) {
        String normalized = value(classification).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return null;
        return PROJECT_CLASSES.contains(normalized) ? normalized : "mods";
    }

    ProjectMeta projectMeta(long projectId) {
        JsonNode project = metadata(projectId);
        validateProject(project, projectId);
        return new ProjectMeta(
                text(project, "name"), text(project, "summary"),
                project.path("logo").path("thumbnail_url").textValue(),
                join(project.path("authors"), "name"), "MOD",
                boundedInt(project.path("download_count").asLong()), null, "curseforge:" + projectId);
    }

    ProjectDetail project(long projectId) {
        JsonNode project = metadata(projectId);
        validateProject(project, projectId);
        JsonNode files = get("/api/v1/hytale/mods/" + projectId + "/files");
        String richDescription = null;
        try {
            richDescription = text(get("/api/v1/hytale/mods/" + projectId + "/description"), "description");
        } catch (ModtaleApiException ignored) {
            // Descriptions are optional. Metadata, versions, and downloads still work without one.
        }
        String providerId = "curseforge:" + projectId;
        String website = project.path("links").path("website").textValue();
        Map<String, String> links = website == null ? Map.of() : Map.of("CurseForge", website);
        return new ProjectDetail(providerId, providerId, text(project, "name"), richDescription,
                text(project, "summary"), null, join(project.path("authors"), "name"),
                project.path("logo").path("thumbnail_url").textValue(), firstScreenshot(project, "url"), "MOD",
                boundedInt(project.path("download_count").asLong()), 0,
                project.path("dates").path("modified").textValue(), null, null, links,
                strings(project.path("categories"), "name"), strings(project.path("screenshots"), "url"),
                Map.of(), false, false, null, versions(files, website, null), List.of(), List.of());
    }

    ProjectSummary enrichBrowseBanner(ProjectSummary summary) {
        if (summary == null || !summary.isCurseForge()
                || (summary.bannerUrl() != null && !summary.bannerUrl().isBlank())) {
            return summary;
        }
        try {
            long projectId = summary.curseForgeProjectId();
            JsonNode project = metadata(projectId);
            validateProject(project, projectId);
            String bannerUrl = firstScreenshot(project, "thumbnail_url");
            if (bannerUrl == null) return summary;
            return new ProjectSummary(
                    summary.id(), summary.slug(), summary.title(), summary.description(), summary.authorId(),
                    summary.author(), summary.imageUrl(), bannerUrl, summary.classification(), summary.downloadCount(),
                    summary.favoriteCount(), summary.updatedAt(), summary.versions(), summary.source(),
                    summary.websiteUrl(), summary.distributionAllowed());
        } catch (RuntimeException ignored) {
            return summary;
        }
    }

    DownloadUrlResponse download(long projectId, long fileId) {
        JsonNode file = get("/api/v1/hytale/mods/" + positive(projectId) + "/files/" + positive(fileId));
        if (file.path("mod_id").asLong() != projectId || file.path("game_id").asLong() != HYTALE_GAME_ID
                || !file.path("is_available").asBoolean(false)) {
            throw new ModtaleApiException("This exact CurseForge file is unavailable.");
        }
        String providerUrl = text(file, "download_url");
        String downloadUrl = approvedDownloadUrl(providerUrl) ? providerUrl
                : CURSEFORGE_SITE + "/api/v1/mods/" + projectId + "/files/" + fileId + "/download";
        Long length = file.path("file_length").asLong(0) > 0 ? file.path("file_length").asLong() : null;
        return new DownloadUrlResponse(downloadUrl, 0, text(file, "file_name"), length,
                hashes(file.path("hashes")), "CURSEFORGE");
    }

    private ProjectSummary summary(JsonNode item, String gameVersion, String projectClass) {
        long id = item.path("id").asLong(0);
        String slug = text(item, "slug");
        if (id <= 0 || slug == null || !slug.matches("[a-z0-9-]+")) return null;
        String website = CURSEFORGE_SITE + "/hytale/" + projectClass + "/" + slug;
        List<ProjectVersion> versions = versions(item.path("recent_files"), website, gameVersion);
        if (!value(gameVersion).isBlank() && versions.isEmpty()) return null;
        String providerId = "curseforge:" + id;
        String updated = versions.isEmpty() ? null : versions.getFirst().releaseDate();
        String bannerUrl = firstScreenshot(item, "thumbnail_url");
        return new ProjectSummary(providerId, providerId, text(item, "name"), text(item, "summary"), null,
                text(item, "primary_author"), text(item, "logo_thumbnail_url"), bannerUrl, "MOD",
                boundedInt(item.path("download_count").asLong()), 0, updated, versions,
                "CURSEFORGE", website, true);
    }

    private JsonNode metadata(long projectId) {
        return get("/api/v1/hytale/mods/" + positive(projectId));
    }

    private String firstScreenshot(JsonNode project, String preferredField) {
        JsonNode first = project.path("screenshots").path(0);
        String preferred = text(first, preferredField);
        if (approvedMediaUrl(preferred)) return preferred;
        String original = text(first, "url");
        return approvedMediaUrl(original) ? original : null;
    }

    private boolean approvedMediaUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1
                    && host != null && (host.equalsIgnoreCase("forgecdn.net")
                    || host.toLowerCase(Locale.ROOT).endsWith(".forgecdn.net"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private List<ProjectVersion> versions(JsonNode files, String website, String gameVersion) {
        if (!files.isArray()) return List.of();
        List<ProjectVersion> result = new ArrayList<>();
        for (JsonNode file : files) {
            long id = file.path("id").asLong(0);
            if (id <= 0) continue;
            List<String> gameVersions = strings(file.path("game_versions"), null);
            if (!value(gameVersion).isBlank()
                    && gameVersions.stream().noneMatch(value(gameVersion)::equalsIgnoreCase)) continue;
            String version = text(file, "display_name");
            if (version == null) version = text(file, "file_name");
            String releaseType = text(file, "release_type");
            result.add(new ProjectVersion(Long.toString(id), version, gameVersions,
                    website == null ? null : website + "/files/" + id,
                    boundedInt(file.path("download_count").asLong()), text(file, "file_date"), null,
                    List.of(), releaseType == null ? null : releaseType.toUpperCase(Locale.ROOT), List.of()));
        }
        result.sort(Comparator.comparing(ProjectVersion::releaseDate,
                Comparator.nullsLast(String::compareTo)).reversed());
        return List.copyOf(result);
    }

    private void validateProject(JsonNode project, long expectedId) {
        String website = project.path("links").path("website").textValue();
        if (project.path("id").asLong() != expectedId || project.path("game_id").asLong() != HYTALE_GAME_ID
                || !project.path("is_available").asBoolean(false) || !hytaleProjectUrl(website)) {
            throw new ModtaleApiException("CurseForge project was not found.", 404, null);
        }
    }

    private JsonNode get(String pathAndQuery) {
        return transport.get(baseUri.resolve(pathAndQuery), JsonNode.class, cacheTtl(pathAndQuery));
    }

    private static Duration cacheTtl(String pathAndQuery) {
        String path = pathAndQuery.split("\\?", 2)[0];
        if (path.endsWith("/search")) return Duration.ofHours(26);
        if (path.matches("/api/v1/hytale/mods/[0-9]+(/description)?")) return Duration.ofDays(7);
        if (path.matches("/api/v1/hytale/mods/[0-9]+/files")) return Duration.ofMinutes(5);
        // Exact file requests may contain expiring download URLs and current availability.
        return Duration.ZERO;
    }

    private void sort(List<ProjectSummary> projects, String sort) {
        String selected = value(sort).toLowerCase(Locale.ROOT);
        Comparator<ProjectSummary> comparator = switch (selected) {
            case "name", "alphabetical" -> Comparator.comparing(
                    project -> value(project.title()), String.CASE_INSENSITIVE_ORDER);
            case "updated", "recently-updated", "created", "newest" -> Comparator.comparing(
                    ProjectSummary::updatedAt, Comparator.nullsLast(String::compareTo)).reversed();
            default -> Comparator.comparingInt(ProjectSummary::downloadCount).reversed();
        };
        projects.sort(comparator.thenComparing(ProjectSummary::id));
    }

    private Map<String, String> hashes(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        addHash(result, "sha1", text(node, "sha1"), 40);
        addHash(result, "md5", text(node, "md5"), 32);
        return Map.copyOf(result);
    }

    private void addHash(Map<String, String> result, String name, String hash, int length) {
        if (hash != null && hash.length() == length && hash.matches("(?i)[a-f0-9]+")) {
            result.put(name, hash.toLowerCase(Locale.ROOT));
        }
    }

    private boolean approvedDownloadUrl(String value) {
        return approvedMediaUrl(value);
    }

    private boolean hytaleProjectUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1
                    && "www.curseforge.com".equalsIgnoreCase(uri.getHost()) && uri.getPath() != null
                    && uri.getPath().matches("/hytale/(mods|prefabs|worlds|bootstrap|translations)/[a-z0-9-]+/?");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private List<String> strings(JsonNode array, String field) {
        if (!array.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : array) {
            String entry = field == null ? item.textValue() : text(item, field);
            if (entry != null && !result.contains(entry)) result.add(entry);
        }
        return List.copyOf(result);
    }

    private String join(JsonNode array, String field) {
        return String.join(", ", strings(array, field));
    }

    private String text(JsonNode node, String field) {
        String result = node.path(field).textValue();
        return result == null || result.isBlank() ? null : result.trim();
    }

    private static int boundedInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, value));
    }

    private static long positive(long value) {
        if (value <= 0) throw new IllegalArgumentException("CurseForge IDs must be positive.");
        return value;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
