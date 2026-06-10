package net.modtale.service.project;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.modtale.config.properties.AppWikiProperties;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.exception.UpstreamServiceException;
import net.modtale.model.project.Project;
import net.modtale.repository.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
public class WikiService {

    private static final Logger logger = LoggerFactory.getLogger(WikiService.class);

    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;
    private final AppWikiProperties wikiProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public WikiService(ProjectRepository projectRepository, ObjectMapper objectMapper, AppWikiProperties wikiProperties) {
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
        this.wikiProperties = wikiProperties;
    }

    public JsonNode getWikiProject(String projectId) {
        String targetSlug = resolveProjectWikiSlug(projectId);
        String wikiId = resolveWikiModId(targetSlug);
        if (wikiId == null) {
            throw new ResourceNotFoundException("Wiki not found for project: " + projectId);
        }
        return readJson(fetchProjectPayload(wikiId), "Failed to parse wiki project data.");
    }

    public JsonNode getWikiPage(String projectId, String pagePath) {
        String targetSlug = resolveProjectWikiSlug(projectId);
        String wikiId = resolveWikiModId(targetSlug);
        if (wikiId == null) {
            throw new ResourceNotFoundException("Wiki not found for project: " + projectId);
        }

        String normalizedPagePath = normalizePagePath(pagePath);
        if (normalizedPagePath.isBlank()) {
            throw new InvalidProjectRequestException("Invalid wiki page path.");
        }
        return readJson(fetchPagePayload(wikiId, normalizedPagePath), "Failed to parse wiki page data.");
    }

    private String resolveProjectWikiSlug(String projectId) {
        Optional<Project> optProject = projectRepository.findById(projectId);
        if (optProject.isEmpty()) {
            throw new ResourceNotFoundException("Wiki not found for project: " + projectId);
        }

        Project project = optProject.get();
        if (!project.isHmWikiEnabled() || project.getHmWikiSlug() == null || project.getHmWikiSlug().isBlank()) {
            throw new ResourceNotFoundException("Wiki not found for project: " + projectId);
        }

        return project.getHmWikiSlug().trim();
    }

    @Cacheable(value = "wikiSlugToId", key = "#hmSlug.toLowerCase()")
    public String resolveWikiModId(String hmSlug) {
        JsonNode root = readJson(fetchModsPayload(), "Failed to parse wiki mods data.");
        JsonNode list = root.isArray() ? root : (root.has("data") ? root.get("data") : null);
        if (list != null && list.isArray()) {
            for (JsonNode node : list) {
                if (node.has("slug") && hmSlug.equalsIgnoreCase(node.get("slug").asText()) && node.has("id")) {
                    return node.get("id").asText();
                }
            }
        }
        return null;
    }

    private JsonNode readJson(String body, String failureMessage) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            logger.error("Wiki JSON parse failure: {}", e.getMessage(), e);
            throw new UpstreamServiceException(HttpStatus.BAD_GATEWAY, failureMessage, e);
        }
    }

    @Cacheable(value = "wikiModsPayload", key = "'mods-list'")
    public String fetchModsPayload() {
        return fetchWithAuthFallback(wikiProperties.wikiUrl() + "/mods");
    }

    @Cacheable(value = "wikiProjectPayload", key = "#id")
    public String fetchProjectPayload(String id) {
        return fetchWithAuthFallback(wikiProperties.wikiUrl() + "/mods/" + id);
    }

    @Cacheable(value = "wikiPagePayload", key = "#id + ':' + #pagePath")
    public String fetchPagePayload(String id, String pagePath) {
        return fetchWithAuthFallback(wikiProperties.wikiUrl() + "/mods/" + id + "/" + pagePath);
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (wikiProperties.wikiKey() != null && !wikiProperties.wikiKey().isBlank()) {
            headers.setBearerAuth(wikiProperties.wikiKey());
        }
        return headers;
    }

    private String normalizePagePath(String pagePath) {
        if (pagePath == null) return "";
        String normalized = pagePath.trim();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        normalized = normalized.replace('\\', '/');
        if (normalized.isBlank()
                || normalized.contains("..")
                || normalized.contains("//")
                || normalized.startsWith(".")) {
            throw new InvalidProjectRequestException("Invalid wiki page path.");
        }
        return normalized;
    }

    private String fetchWithAuthFallback(String url) {
        try {
            return exchangeForBody(url, buildHeaders());
        } catch (HttpStatusCodeException e) {
            HttpStatusCode code = e.getStatusCode();
            boolean authConfigured = wikiProperties.wikiKey() != null && !wikiProperties.wikiKey().isBlank();
            boolean authRejected = code.value() == 401 || code.value() == 403;

            if (authConfigured && authRejected) {
                logger.warn("Wiki API rejected configured bearer token ({}). Retrying unauthenticated for URL: {}", code, url);
                return exchangeForBody(url, new HttpHeaders());
            }

            HttpStatus status = HttpStatus.resolve(code.value());
            throw new UpstreamServiceException(
                    status != null ? status : HttpStatus.BAD_GATEWAY,
                    "Wiki upstream request failed.",
                    e
            );
        } catch (RestClientException e) {
            logger.error("Wiki upstream transport failure for URL '{}': {}", url, e.getMessage(), e);
            throw new UpstreamServiceException(HttpStatus.BAD_GATEWAY, "Wiki upstream is temporarily unavailable.", e);
        }
    }

    private String exchangeForBody(String url, HttpHeaders headers) {
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );
        return response.getBody();
    }
}
