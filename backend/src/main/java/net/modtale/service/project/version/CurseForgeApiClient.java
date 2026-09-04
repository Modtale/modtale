package net.modtale.service.project.version;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.modtale.config.properties.AppCurseForgeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class CurseForgeApiClient {

    private static final int MAX_FILES = 20;
    private static final int MAX_CACHE_ENTRIES = 500;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String API_BASE = "https://api.curseforge.com";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AppCurseForgeProperties properties;
    private final RestTemplate restTemplate;
    private final ConcurrentMap<String, CachedProject> cache = new ConcurrentHashMap<>();

    @Autowired
    public CurseForgeApiClient(AppCurseForgeProperties properties) {
        this(properties, createRestTemplate());
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(requestFactory);
    }

    CurseForgeApiClient(AppCurseForgeProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public Optional<CurseForgeProject> resolveProject(String slug, String requestedFileId) {
        if (!properties.isConfigured() || slug == null || slug.isBlank()
                || (requestedFileId != null && !requestedFileId.matches("[0-9]+"))) {
            return Optional.empty();
        }

        String cacheKey = slug.toLowerCase(Locale.ROOT) + ":" + (requestedFileId == null ? "latest" : requestedFileId);
        CachedProject cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return Optional.of(cached.project());
        }
        if (cached != null) cache.remove(cacheKey, cached);

        try {
            URI searchUri = UriComponentsBuilder.fromUriString(API_BASE)
                    .path("/v1/mods/search")
                    .queryParam("gameId", properties.hytaleGameId())
                    .queryParam("slug", slug)
                    .queryParam("pageSize", 1)
                    .build()
                    .encode()
                    .toUri();
            JsonNode projectEnvelope = get(searchUri);
            JsonNode projects = projectEnvelope.path("data");
            if (!projects.isArray() || projects.isEmpty()) {
                return Optional.empty();
            }

            JsonNode project = projects.get(0);
            String resolvedSlug = text(project, "slug");
            long projectId = project.path("id").asLong(0);
            if (projectId <= 0 || resolvedSlug == null || !resolvedSlug.equalsIgnoreCase(slug)
                    || project.path("gameId").asLong(0) != properties.hytaleGameId()
                    || !project.path("isAvailable").asBoolean(false)) {
                return Optional.empty();
            }

            List<CurseForgeFile> files = requestedFileId == null
                    ? getRecentFiles(projectId)
                    : getExactFile(projectId, requestedFileId);
            if (requestedFileId != null && files.isEmpty()) return Optional.empty();

            CurseForgeProject resolved = new CurseForgeProject(
                    Long.toString(projectId),
                    resolvedSlug,
                    text(project, "name"),
                    text(project, "summary"),
                    project.path("logo").path("thumbnailUrl").textValue(),
                    project.has("allowModDistribution") ? project.path("allowModDistribution").booleanValue() : null,
                    files
            );
            if (cache.size() >= MAX_CACHE_ENTRIES) cache.clear();
            cache.put(cacheKey, new CachedProject(resolved, Instant.now().plus(CACHE_TTL)));
            return Optional.of(resolved);
        } catch (RestClientException | IllegalArgumentException | java.io.IOException ex) {
            return Optional.empty();
        }
    }

    private List<CurseForgeFile> getRecentFiles(long projectId) throws java.io.IOException {
        URI filesUri = UriComponentsBuilder.fromUriString(API_BASE)
                .path("/v1/mods/{projectId}/files")
                .queryParam("pageSize", 50)
                .buildAndExpand(projectId)
                .encode()
                .toUri();
        return parseFiles(get(filesUri).path("data"), projectId);
    }

    private List<CurseForgeFile> getExactFile(long projectId, String fileId) throws java.io.IOException {
        URI fileUri = UriComponentsBuilder.fromUriString(API_BASE)
                .path("/v1/mods/{projectId}/files/{fileId}")
                .buildAndExpand(projectId, fileId)
                .encode()
                .toUri();
        CurseForgeFile file = parseFile(get(fileUri).path("data"), projectId);
        return file == null ? List.of() : List.of(file);
    }

    private JsonNode get(URI uri) throws java.io.IOException {
        RequestEntity<Void> request = RequestEntity.get(uri)
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.USER_AGENT, "Modtale/1.0 (+https://modtale.net)")
                .header("x-api-key", properties.apiKey().trim())
                .build();
        String body = restTemplate.exchange(request, String.class).getBody();
        if (body == null || body.isBlank()) {
            throw new java.io.IOException("CurseForge returned an empty response.");
        }
        return OBJECT_MAPPER.readTree(body);
    }

    private List<CurseForgeFile> parseFiles(JsonNode data, long projectId) {
        if (!data.isArray()) {
            return List.of();
        }
        List<CurseForgeFile> files = new ArrayList<>();
        for (JsonNode file : data) {
            CurseForgeFile parsed = parseFile(file, projectId);
            if (parsed != null) files.add(parsed);
        }
        files.sort(Comparator.comparing(CurseForgeFile::fileDate, Comparator.nullsLast(String::compareTo)).reversed());
        return files.stream().limit(MAX_FILES).toList();
    }

    private CurseForgeFile parseFile(JsonNode file, long projectId) {
        long fileId = file.path("id").asLong(0);
        if (fileId <= 0 || file.path("modId").asLong(0) != projectId || !file.path("isAvailable").asBoolean(false)) {
            return null;
        }
        return new CurseForgeFile(
                    Long.toString(fileId),
                    text(file, "displayName"),
                    text(file, "fileName"),
                    inferVersion(file),
                    releaseType(file.path("releaseType").asInt(0)),
                    text(file, "fileDate"),
                    file.path("fileLength").canConvertToLong() && file.path("fileLength").asLong() > 0
                            ? file.path("fileLength").asLong() : null,
                    parseHashes(file.path("hashes")),
                    parseGameVersions(file.path("gameVersions")),
                    file.path("fileStatus").canConvertToInt() && file.path("fileStatus").asInt() > 0
                            ? file.path("fileStatus").asInt() : null,
                    true
            );
    }

    private Map<String, String> parseHashes(JsonNode hashes) {
        if (!hashes.isArray()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode hash : hashes) {
            String algorithm = switch (hash.path("algo").asInt(0)) {
                case 1 -> "sha1";
                case 2 -> "md5";
                default -> null;
            };
            String value = text(hash, "value");
            if (algorithm != null && value != null && value.matches("(?i)[a-f0-9]+")) {
                int expectedLength = "sha1".equals(algorithm) ? 40 : 32;
                if (value.length() == expectedLength) result.put(algorithm, value.toLowerCase(Locale.ROOT));
            }
        }
        return Map.copyOf(result);
    }

    private List<String> parseGameVersions(JsonNode versions) {
        if (!versions.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode version : versions) {
            String value = version.textValue();
            if (value != null && !value.isBlank() && !result.contains(value.trim())) result.add(value.trim());
        }
        return List.copyOf(result);
    }

    private String inferVersion(JsonNode file) {
        String displayName = text(file, "displayName");
        return displayName == null ? text(file, "fileName") : displayName;
    }

    private String releaseType(int value) {
        return switch (value) {
            case 1 -> "RELEASE";
            case 2 -> "BETA";
            case 3 -> "ALPHA";
            default -> null;
        };
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).textValue();
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CurseForgeProject(
            String id,
            String slug,
            String title,
            String summary,
            String iconUrl,
            Boolean distributionAllowed,
            List<CurseForgeFile> files
    ) {}

    public record CurseForgeFile(
            String id,
            String displayName,
            String fileName,
            String versionNumber,
            String releaseType,
            String fileDate,
            Long fileSize,
            Map<String, String> hashes,
            List<String> gameVersions,
            Integer fileStatus,
            boolean available
    ) {}

    private record CachedProject(CurseForgeProject project, Instant expiresAt) {}
}
