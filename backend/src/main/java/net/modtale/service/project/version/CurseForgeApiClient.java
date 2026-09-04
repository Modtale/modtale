package net.modtale.service.project.version;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.modtale.exception.UpstreamServiceException;
import net.modtale.model.dto.project.CurseForgeCommentsDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class CurseForgeApiClient {
    private static final int HYTALE_GAME_ID = 70216;
    private static final int MAX_FILES = 50;
    private static final int MAX_IDENTITY_CANDIDATES = 200;
    private static final int MAX_COMMENT_PAGES = 20;
    private static final String WEBSITE_BASE = "https://www.curseforge.com";
    private static final String NYOCF_BASE = "https://nyocf.junyo.dev";
    private static final String USER_AGENT = "Modtale/1.0 (+https://modtale.net)";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;

    public CurseForgeApiClient() {
        this(createRestTemplate());
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        return new RestTemplate(requestFactory);
    }

    CurseForgeApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean isConfigured() {
        return true;
    }

    public Optional<CurseForgeProject> resolveProject(String slug, String requestedFileId) {
        if (slug == null || !slug.trim().matches("[A-Za-z0-9][A-Za-z0-9-]*")
                || (requestedFileId != null && !requestedFileId.matches("[0-9]+"))) return Optional.empty();
        return fetchProject(slug.trim(), requestedFileId);
    }

    public CurseForgeSearchResult searchMods(String search, String gameVersion, int page, int pageSize, String sort) {
        int safePage = Math.max(0, page);
        int safePageSize = Math.max(1, Math.min(50, pageSize));
        URI uri = UriComponentsBuilder.fromUriString(NYOCF_BASE)
                .path("/api/v1/hytale/mods/search")
                .queryParam("q", search == null ? "" : search.trim())
                .queryParam("limit", safePageSize)
                .queryParam("offset", safePage * safePageSize)
                .queryParam("include_files", true).build().encode().toUri();
        try {
            JsonNode envelope = getJson(uri);
            List<CurseForgeProject> projects = new ArrayList<>();
            if (envelope.path("data").isArray()) for (JsonNode item : envelope.path("data")) {
                CurseForgeProject parsed = parseSearchProject(item, gameVersion);
                if (parsed != null && (isBlank(gameVersion) || !parsed.files().isEmpty())) projects.add(parsed);
            }
            sortProjects(projects, sort);
            long total = Math.max(projects.size(), envelope.path("pagination").path("total").asLong(projects.size()));
            return new CurseForgeSearchResult(List.copyOf(projects), safePage * safePageSize, safePageSize, total);
        } catch (RestClientException | java.io.IOException ex) {
            throw new UpstreamServiceException(HttpStatus.BAD_GATEWAY, "CurseForge catalog is unavailable.", ex);
        }
    }

    public Optional<CurseForgeProject> getProject(long projectId) {
        return projectId <= 0 ? Optional.empty() : fetchProject(Long.toString(projectId), null);
    }

    public Optional<CurseForgeDownload> getDownload(long projectId, long fileId) {
        if (projectId <= 0 || fileId <= 0) return Optional.empty();
        try {
            JsonNode node = getJson(exactFileUri(projectId, fileId));
            CurseForgeFile file = parseExactFile(node, projectId);
            if (file == null) return Optional.empty();
            String providerUrl = text(node, "download_url");
            String downloadUrl = isApprovedDownloadUrl(providerUrl) ? providerUrl
                    : WEBSITE_BASE + "/api/v1/mods/" + projectId + "/files/" + fileId + "/download";
            return Optional.of(new CurseForgeDownload(downloadUrl, file.fileName(), file.fileSize(), file.hashes()));
        } catch (RestClientException | java.io.IOException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public CurseForgeCommentsDTO getComments(long projectId) {
        if (projectId <= 0) return new CurseForgeCommentsDTO(List.of(), 0);
        try {
            List<CurseForgeCommentsDTO.Comment> comments = new ArrayList<>();
            Set<Long> seen = new LinkedHashSet<>();
            long totalCount = 0;
            for (int page = 0; page < MAX_COMMENT_PAGES; page++) {
                JsonNode envelope = getJson(UriComponentsBuilder.fromUriString(WEBSITE_BASE)
                        .path("/api/v1/mods/{projectId}/comments")
                        .queryParam("index", page)
                        .buildAndExpand(projectId).encode().toUri());
                JsonNode data = envelope.path("data");
                totalCount = Math.max(totalCount, envelope.path("pagination").path("totalCount").asLong(0));
                if (!data.isArray() || data.isEmpty()) break;
                for (JsonNode node : data) {
                    CurseForgeCommentsDTO.Comment comment = parseComment(node, projectId, seen, 0);
                    if (comment != null) comments.add(comment);
                }
                if (countComments(comments) >= totalCount && totalCount > 0) break;
            }
            long importedCount = countComments(comments);
            return new CurseForgeCommentsDTO(List.copyOf(comments), Math.max(totalCount, importedCount));
        } catch (RestClientException | java.io.IOException ex) {
            throw new UpstreamServiceException(HttpStatus.BAD_GATEWAY, "CurseForge comments are unavailable.", ex);
        }
    }

    /** A filename locates candidates; only nyoCF's exact-file fingerprint proves identity. */
    public Map<Long, CurseForgeFingerprintMatch> matchArtifacts(List<CurseForgeArtifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) return Map.of();
        Map<String, List<CurseForgeArtifact>> byFileName = new LinkedHashMap<>();
        for (CurseForgeArtifact artifact : artifacts) {
            if (artifact == null || artifact.fingerprint() < 0 || isBlank(artifact.fileName())) continue;
            String fileName = baseName(artifact.fileName());
            if (!isBlank(fileName)) byFileName.computeIfAbsent(fileName, ignored -> new ArrayList<>()).add(artifact);
            if (byFileName.size() >= 100) break;
        }
        if (byFileName.isEmpty()) return Map.of();
        try {
            JsonNode results = postJson(UriComponentsBuilder.fromUriString(NYOCF_BASE)
                    .path("/api/v1/hytale/mods/batch-search").build().toUri(),
                    Map.of("queries", List.copyOf(byFileName.keySet()))).path("results");
            Map<Long, CurseForgeFingerprintMatch> matches = new LinkedHashMap<>();
            Map<Long, List<CurseForgeFile>> filesByProject = new HashMap<>();
            Set<String> inspected = new LinkedHashSet<>();
            int candidateCount = 0;
            for (Map.Entry<String, List<CurseForgeArtifact>> query : byFileName.entrySet()) {
                JsonNode candidates = results.path(query.getKey());
                if (!candidates.isArray()) continue;
                for (JsonNode candidate : candidates) {
                    long projectId = candidate.path("id").asLong(0);
                    if (projectId <= 0 || ++candidateCount > MAX_IDENTITY_CANDIDATES) break;
                    List<CurseForgeFile> files = filesByProject.computeIfAbsent(projectId, this::fetchFilesQuietly);
                    for (CurseForgeFile listed : files) {
                        if (!query.getKey().equalsIgnoreCase(listed.fileName())) continue;
                        String exactKey = projectId + ":" + listed.id();
                        if (!inspected.add(exactKey)) continue;
                        CurseForgeFile exact = fetchExactFileQuietly(projectId, Long.parseLong(listed.id()));
                        if (exact == null || exact.fingerprint() == null) continue;
                        for (CurseForgeArtifact artifact : query.getValue()) {
                            if (exact.fingerprint().equals(artifact.fingerprint())) matches.putIfAbsent(
                                    artifact.fingerprint(), new CurseForgeFingerprintMatch(projectId, Long.parseLong(exact.id())));
                        }
                    }
                }
            }
            return Map.copyOf(matches);
        } catch (RestClientException | java.io.IOException ex) {
            return Map.of();
        }
    }

    private Optional<CurseForgeProject> fetchProject(String idOrSlug, String requestedFileId) {
        try {
            JsonNode project = getJson(UriComponentsBuilder.fromUriString(NYOCF_BASE)
                    .path("/api/v1/hytale/mods/{id}").buildAndExpand(idOrSlug).encode().toUri());
            long projectId = project.path("id").asLong(0);
            String slug = text(project, "slug");
            String websiteUrl = project.path("links").path("website").textValue();
            if (projectId <= 0 || project.path("game_id").asLong(0) != HYTALE_GAME_ID
                    || !project.path("is_available").asBoolean(false) || slug == null
                    || !isHytaleProjectUrl(websiteUrl)) return Optional.empty();
            List<CurseForgeFile> files;
            if (requestedFileId == null) files = fetchFiles(projectId).stream().limit(MAX_FILES).toList();
            else {
                CurseForgeFile exact = fetchExactFile(projectId, Long.parseLong(requestedFileId));
                if (exact == null) return Optional.empty();
                files = List.of(exact);
            }
            String description = null;
            try {
                description = text(getJson(UriComponentsBuilder.fromUriString(NYOCF_BASE)
                        .path("/api/v1/hytale/mods/{id}/description").buildAndExpand(projectId).encode().toUri()), "description");
            } catch (RestClientException | java.io.IOException ignored) {
                // Rich descriptions are optional; metadata, versions, and installs remain available.
            }
            return Optional.of(new CurseForgeProject(Long.toString(projectId), slug, text(project, "name"),
                    text(project, "summary"), project.path("logo").path("thumbnail_url").textValue(), true,
                    files, websiteUrl, strings(project.path("authors"), "name"), strings(project.path("categories"), "name"),
                    strings(project.path("screenshots"), "url"), project.path("dates").path("modified").textValue(),
                    Math.max(0, project.path("download_count").asLong(0)), description));
        } catch (RestClientException | IllegalArgumentException | java.io.IOException ex) {
            return Optional.empty();
        }
    }

    private List<CurseForgeFile> fetchFiles(long projectId) throws java.io.IOException {
        return parseFiles(getJson(UriComponentsBuilder.fromUriString(NYOCF_BASE)
                .path("/api/v1/hytale/mods/{id}/files").buildAndExpand(projectId).encode().toUri()), projectId, null);
    }

    private List<CurseForgeFile> fetchFilesQuietly(long projectId) {
        try { return fetchFiles(projectId); }
        catch (RestClientException | java.io.IOException ex) { return List.of(); }
    }

    private CurseForgeFile fetchExactFile(long projectId, long fileId) throws java.io.IOException {
        return parseExactFile(getJson(exactFileUri(projectId, fileId)), projectId);
    }

    private CurseForgeFile fetchExactFileQuietly(long projectId, long fileId) {
        try { return fetchExactFile(projectId, fileId); }
        catch (RestClientException | java.io.IOException ex) { return null; }
    }

    private URI exactFileUri(long projectId, long fileId) {
        return UriComponentsBuilder.fromUriString(NYOCF_BASE)
                .path("/api/v1/hytale/mods/{projectId}/files/{fileId}")
                .buildAndExpand(projectId, fileId).encode().toUri();
    }

    private CurseForgeProject parseSearchProject(JsonNode item, String gameVersion) {
        long projectId = item.path("id").asLong(0);
        String slug = text(item, "slug");
        if (projectId <= 0 || slug == null || !slug.matches("[a-z0-9-]+")) return null;
        List<CurseForgeFile> files = parseFiles(item.path("recent_files"), projectId, gameVersion);
        String modified = files.isEmpty() ? null : files.getFirst().fileDate();
        String author = text(item, "primary_author");
        return new CurseForgeProject(Long.toString(projectId), slug, text(item, "name"), text(item, "summary"),
                text(item, "logo_thumbnail_url"), true, files, WEBSITE_BASE + "/hytale/mods/" + slug,
                author == null ? List.of() : List.of(author), strings(item.path("categories"), null), List.of(),
                modified, Math.max(0, item.path("download_count").asLong(0)), null);
    }

    private List<CurseForgeFile> parseFiles(JsonNode nodes, long projectId, String gameVersion) {
        if (!nodes.isArray()) return List.of();
        List<CurseForgeFile> files = new ArrayList<>();
        for (JsonNode node : nodes) {
            List<String> versions = parseGameVersions(node.path("game_versions"));
            if (!isBlank(gameVersion) && versions.stream().noneMatch(gameVersion.trim()::equalsIgnoreCase)) continue;
            CurseForgeFile file = parseFile(node, true);
            if (file != null) files.add(file);
        }
        files.sort(Comparator.comparing(CurseForgeFile::fileDate, Comparator.nullsLast(String::compareTo)).reversed());
        return List.copyOf(files);
    }

    private CurseForgeFile parseExactFile(JsonNode node, long projectId) {
        if (node.path("mod_id").asLong(0) != projectId || node.path("game_id").asLong(0) != HYTALE_GAME_ID
                || !node.path("is_available").asBoolean(false)) return null;
        return parseFile(node, false);
    }

    private CurseForgeFile parseFile(JsonNode node, boolean listed) {
        long id = node.path("id").asLong(0);
        if (id <= 0) return null;
        String releaseType = text(node, "release_type");
        String displayName = text(node, "display_name");
        String fileName = text(node, "file_name");
        JsonNode hashes = node.path("hashes");
        Long fingerprint = hashes.path("fingerprint").canConvertToLong() && hashes.path("fingerprint").asLong() >= 0
                ? hashes.path("fingerprint").asLong() : null;
        return new CurseForgeFile(Long.toString(id), displayName, fileName, displayName == null ? fileName : displayName,
                releaseType == null ? null : releaseType.toUpperCase(Locale.ROOT), text(node, "file_date"),
                node.path("file_length").asLong(0) > 0 ? node.path("file_length").asLong() : null,
                parseHashes(hashes), parseGameVersions(node.path("game_versions")), null,
                listed || node.path("is_available").asBoolean(false), Math.max(0, node.path("download_count").asLong(0)), fingerprint);
    }

    private CurseForgeCommentsDTO.Comment parseComment(JsonNode node, long projectId, Set<Long> seen, int depth) {
        long id = node.path("id").asLong(0);
        if (id <= 0 || node.path("projectId").asLong(projectId) != projectId || !seen.add(id) || depth > 20) return null;
        String content = text(node, "text");
        if (content == null) return null;
        JsonNode authorNode = node.path("author");
        String username = firstNonBlank(text(authorNode, "displayName"), text(authorNode, "username"), "CurseForge user");
        String avatarUrl = text(authorNode, "twitchAvatarUrl");
        if (avatarUrl != null) avatarUrl = avatarUrl.replace("{0}", "70x70");
        List<CurseForgeCommentsDTO.Comment> replies = new ArrayList<>();
        JsonNode replyNodes = node.path("replies");
        if (replyNodes.isArray()) for (JsonNode replyNode : replyNodes) {
            CurseForgeCommentsDTO.Comment reply = parseComment(replyNode, projectId, seen, depth + 1);
            if (reply != null) replies.add(reply);
        }
        long postedAt = node.path("datePosted").asLong(0);
        String date = postedAt > 0 ? Instant.ofEpochMilli(postedAt).toString() : null;
        return new CurseForgeCommentsDTO.Comment("curseforge:" + id, username,
                new CurseForgeCommentsDTO.Author(username, avatarUrl), content, date,
                node.path("isPinned").asBoolean(false), true, List.copyOf(replies));
    }

    private static long countComments(List<CurseForgeCommentsDTO.Comment> comments) {
        long count = 0;
        for (CurseForgeCommentsDTO.Comment comment : comments) count += 1 + countComments(comment.replies());
        return count;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private JsonNode getJson(URI uri) throws java.io.IOException {
        RequestEntity<Void> request = RequestEntity.get(uri).header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.USER_AGENT, USER_AGENT).build();
        return readJson(restTemplate.exchange(request, String.class).getBody());
    }

    private JsonNode postJson(URI uri, Object body) throws java.io.IOException {
        RequestEntity<Object> request = RequestEntity.post(uri).header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.CONTENT_TYPE, "application/json").header(HttpHeaders.USER_AGENT, USER_AGENT).body(body);
        return readJson(restTemplate.exchange(request, String.class).getBody());
    }

    private JsonNode readJson(String body) throws java.io.IOException {
        if (body == null || body.isBlank()) throw new java.io.IOException("nyoCF returned an empty response.");
        return OBJECT_MAPPER.readTree(body);
    }

    private void sortProjects(List<CurseForgeProject> projects, String sort) {
        String normalized = sort == null ? "downloads" : sort.trim().toLowerCase(Locale.ROOT);
        Comparator<CurseForgeProject> comparator = switch (normalized) {
            case "name", "alphabetical" -> Comparator.comparing(
                    project -> Optional.ofNullable(project.title()).orElse(""), String.CASE_INSENSITIVE_ORDER);
            case "updated", "recently-updated", "created", "newest" -> Comparator.comparing(
                    CurseForgeProject::dateModified, Comparator.nullsLast(String::compareTo)).reversed();
            default -> Comparator.comparingLong(CurseForgeProject::downloadCount).reversed();
        };
        projects.sort(comparator.thenComparing(CurseForgeProject::id));
    }

    private boolean isHytaleProjectUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1
                    && "www.curseforge.com".equalsIgnoreCase(uri.getHost()) && uri.getPath() != null
                    && uri.getPath().matches("/hytale/mods/[a-z0-9-]+/?");
        } catch (IllegalArgumentException ex) { return false; }
    }

    private boolean isApprovedDownloadUrl(String value) {
        if (isBlank(value)) return false;
        URI uri = URI.create(value.trim());
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1
                && host != null && (host.equalsIgnoreCase("forgecdn.net") || host.toLowerCase(Locale.ROOT).endsWith(".forgecdn.net"));
    }

    private Map<String, String> parseHashes(JsonNode hashes) {
        if (!hashes.isObject()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        addHash(result, "sha1", text(hashes, "sha1"), 40);
        addHash(result, "md5", text(hashes, "md5"), 32);
        return Map.copyOf(result);
    }

    private void addHash(Map<String, String> hashes, String algorithm, String value, int length) {
        if (value != null && value.length() == length && value.matches("(?i)[a-f0-9]+"))
            hashes.put(algorithm, value.toLowerCase(Locale.ROOT));
    }

    private List<String> parseGameVersions(JsonNode versions) {
        if (!versions.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode version : versions) {
            String value = version.textValue();
            if (!isBlank(value) && !result.contains(value.trim())) result.add(value.trim());
        }
        return List.copyOf(result);
    }

    private List<String> strings(JsonNode array, String field) {
        if (!array.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonNode item : array) {
            String value = field == null ? item.textValue() : text(item, field);
            if (!isBlank(value) && !values.contains(value.trim())) values.add(value.trim());
        }
        return List.copyOf(values);
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).textValue();
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

    private static String baseName(String value) {
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash < 0 ? value : value.substring(slash + 1);
    }

    public record CurseForgeProject(String id, String slug, String title, String summary, String iconUrl,
            Boolean distributionAllowed, List<CurseForgeFile> files, String websiteUrl, List<String> authors,
            List<String> categories, List<String> screenshots, String dateModified, long downloadCount, String description) {
        public CurseForgeProject(String id, String slug, String title, String summary, String iconUrl,
                Boolean distributionAllowed, List<CurseForgeFile> files) {
            this(id, slug, title, summary, iconUrl, distributionAllowed, files,
                    null, List.of(), List.of(), List.of(), null, 0, null);
        }
    }

    public record CurseForgeFile(String id, String displayName, String fileName, String versionNumber,
            String releaseType, String fileDate, Long fileSize, Map<String, String> hashes, List<String> gameVersions,
            Integer fileStatus, boolean available, long downloadCount, Long fingerprint) {
        public CurseForgeFile(String id, String displayName, String fileName, String versionNumber, String releaseType,
                String fileDate, Long fileSize, Map<String, String> hashes, List<String> gameVersions,
                Integer fileStatus, boolean available, long downloadCount) {
            this(id, displayName, fileName, versionNumber, releaseType, fileDate, fileSize, hashes,
                    gameVersions, fileStatus, available, downloadCount, null);
        }
        public CurseForgeFile(String id, String displayName, String fileName, String versionNumber, String releaseType,
                String fileDate, Long fileSize, Map<String, String> hashes, List<String> gameVersions,
                Integer fileStatus, boolean available) {
            this(id, displayName, fileName, versionNumber, releaseType, fileDate, fileSize, hashes,
                    gameVersions, fileStatus, available, 0, null);
        }
    }

    public record CurseForgeSearchResult(List<CurseForgeProject> projects, int index, int pageSize, long totalCount) {}
    public record CurseForgeDownload(String downloadUrl, String fileName, Long fileSize, Map<String, String> hashes) {}
    public record CurseForgeArtifact(long fingerprint, String fileName) {}
    public record CurseForgeFingerprintMatch(long projectId, long fileId) {}
}
