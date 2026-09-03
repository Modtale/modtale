package net.modtale.launcher.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.modtale.launcher.model.project.DownloadUrlResponse;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectPage;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.model.project.ProjectVersion;

/** Direct, keyless CurseForge catalog access through nyoCF. */
final class NyoCfClient {

    private static final URI DEFAULT_BASE_URI = URI.create("https://nyocf.junyo.dev");
    private static final String CURSEFORGE_SITE = "https://www.curseforge.com";
    private static final long HYTALE_GAME_ID = 70216;
    private static final int BANNER_LOOKUP_BATCH_SIZE = 4;
    private final HttpClient httpClient;
    private final URI baseUri;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentMap<Long, JsonNode> projectMetadata = new ConcurrentHashMap<>();

    NyoCfClient(HttpClient httpClient) {
        this(httpClient, DEFAULT_BASE_URI);
    }

    NyoCfClient(HttpClient httpClient, URI baseUri) {
        this.httpClient = httpClient;
        this.baseUri = baseUri;
    }

    ProjectPage search(ProjectSearchQuery query) {
        int page = Math.max(0, query.page());
        int size = Math.max(1, Math.min(50, query.size()));
        String path = "/api/v1/hytale/mods/search?q=" + encode(value(query.search()))
                + "&limit=" + size + "&offset=" + (page * size) + "&include_files=true";
        JsonNode envelope = get(path);
        List<ProjectSummary> projects = new ArrayList<>();
        for (JsonNode item : envelope.path("data")) {
            ProjectSummary project = summary(item, query.gameVersion());
            if (project != null) projects.add(project);
        }
        projects = withGalleryBanners(projects);
        sort(projects, query.sort());
        long total = Math.max(projects.size(), envelope.path("pagination").path("total").asLong(projects.size()));
        int pages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new ProjectPage(List.copyOf(projects), pages, total, page, page + 1 >= pages);
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

    private ProjectSummary summary(JsonNode item, String gameVersion) {
        long id = item.path("id").asLong(0);
        String slug = text(item, "slug");
        if (id <= 0 || slug == null || !slug.matches("[a-z0-9-]+")) return null;
        String website = CURSEFORGE_SITE + "/hytale/mods/" + slug;
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

    private List<ProjectSummary> withGalleryBanners(List<ProjectSummary> projects) {
        List<ProjectSummary> enriched = new ArrayList<>(projects.size());
        for (int start = 0; start < projects.size(); start += BANNER_LOOKUP_BATCH_SIZE) {
            int end = Math.min(start + BANNER_LOOKUP_BATCH_SIZE, projects.size());
            List<CompletableFuture<ProjectSummary>> batch = projects.subList(start, end).stream()
                    .map(project -> CompletableFuture.supplyAsync(() -> withGalleryBanner(project)))
                    .toList();
            batch.stream().map(CompletableFuture::join).forEach(enriched::add);
        }
        return enriched;
    }

    private ProjectSummary withGalleryBanner(ProjectSummary summary) {
        if (summary.bannerUrl() != null && !summary.bannerUrl().isBlank()) return summary;
        try {
            long projectId = summary.curseForgeProjectId();
            JsonNode metadata = metadata(projectId);
            validateProject(metadata, projectId);
            String bannerUrl = firstScreenshot(metadata, "thumbnail_url");
            if (bannerUrl == null) return summary;
            return new ProjectSummary(
                    summary.id(), summary.slug(), summary.title(), summary.description(), summary.authorId(),
                    summary.author(), summary.imageUrl(), bannerUrl, summary.classification(), summary.downloadCount(),
                    summary.favoriteCount(), summary.updatedAt(), summary.versions(), summary.source(),
                    summary.websiteUrl(), summary.distributionAllowed()
            );
        } catch (RuntimeException ignored) {
            return summary;
        }
    }

    private JsonNode metadata(long projectId) {
        return projectMetadata.computeIfAbsent(positive(projectId),
                id -> get("/api/v1/hytale/mods/" + id));
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
        URI uri = baseUri.resolve(pathAndQuery);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "ModtaleLauncher/0.1 (+https://modtale.net)").GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ModtaleApiTransport.ensureSuccess(response.statusCode(), uri.toString());
            return mapper.readTree(response.body());
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not read CurseForge catalog data.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ModtaleApiException("CurseForge catalog request was interrupted.", ex);
        }
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
                    && uri.getPath().matches("/hytale/mods/[a-z0-9-]+/?");
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
