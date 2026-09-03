package net.modtale.service.project.version;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.modtale.config.properties.AppCurseForgeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class CurseForgeApiClient {

    private static final int MAX_FILES = 20;
    private static final String API_BASE = "https://api.curseforge.com";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AppCurseForgeProperties properties;
    private final RestTemplate restTemplate;

    @Autowired
    public CurseForgeApiClient(AppCurseForgeProperties properties) {
        this(properties, new RestTemplate());
    }

    CurseForgeApiClient(AppCurseForgeProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    public Optional<CurseForgeProject> resolveProject(String slug, String requestedFileId) {
        if (!properties.isConfigured() || slug == null || slug.isBlank()) {
            return Optional.empty();
        }

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
                    || !project.path("isAvailable").asBoolean(true)) {
                return Optional.empty();
            }

            URI filesUri = UriComponentsBuilder.fromUriString(API_BASE)
                    .path("/v1/mods/{projectId}/files")
                    .queryParam("pageSize", 50)
                    .buildAndExpand(projectId)
                    .encode()
                    .toUri();
            JsonNode filesEnvelope = get(filesUri);
            List<CurseForgeFile> files = parseFiles(filesEnvelope.path("data"), projectId);
            if (requestedFileId != null && files.stream().noneMatch(file -> requestedFileId.equals(file.id()))) {
                return Optional.empty();
            }

            return Optional.of(new CurseForgeProject(
                    Long.toString(projectId),
                    resolvedSlug,
                    text(project, "name"),
                    text(project, "summary"),
                    project.path("logo").path("thumbnailUrl").textValue(),
                    files
            ));
        } catch (RestClientException | IllegalArgumentException | java.io.IOException ex) {
            return Optional.empty();
        }
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
            long fileId = file.path("id").asLong(0);
            if (fileId <= 0 || file.path("modId").asLong(0) != projectId || !file.path("isAvailable").asBoolean(true)) {
                continue;
            }
            files.add(new CurseForgeFile(
                    Long.toString(fileId),
                    text(file, "displayName"),
                    text(file, "fileName"),
                    inferVersion(file),
                    releaseType(file.path("releaseType").asInt(0)),
                    text(file, "fileDate")
            ));
        }
        files.sort(Comparator.comparing(CurseForgeFile::fileDate, Comparator.nullsLast(String::compareTo)).reversed());
        return files.stream().limit(MAX_FILES).toList();
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
            List<CurseForgeFile> files
    ) {}

    public record CurseForgeFile(
            String id,
            String displayName,
            String fileName,
            String versionNumber,
            String releaseType,
            String fileDate
    ) {}
}
