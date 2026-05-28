package net.modtale.service.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.modtale.model.project.Project;
import net.modtale.repository.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatusCode;

import java.util.Optional;

@Service
public class WikiService {

    private static final Logger logger = LoggerFactory.getLogger(WikiService.class);

    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.hytalemodding.wiki-key:}")
    private String wikiApiKey;

    @Value("${app.hytalemodding.wiki-url:https://wiki.hytalemodding.dev/api}")
    private String wikiApiUrl;

    public WikiService(ProjectRepository projectRepository, ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<?> getWikiProject(String slug) {
        String targetSlug = resolveTargetWikiSlug(slug);
        String id = resolveWikiModId(targetSlug);
        if (id == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wiki not found for: " + slug);

        try {
            String body = fetchProjectPayload(id);
            return ResponseEntity.ok().body(objectMapper.readTree(body));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (RestClientException e) {
            logger.error("Wiki project fetch transport failure for slug '{}': {}", slug, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Wiki upstream is temporarily unavailable.");
        } catch (Exception e) {
            logger.error("Unexpected wiki project fetch error for slug '{}': {}", slug, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Failed to fetch wiki project data.");
        }
    }

    public ResponseEntity<?> getWikiPage(String slug, String pagePath) {
        String targetSlug = resolveTargetWikiSlug(slug);
        String id = resolveWikiModId(targetSlug);
        if (id == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wiki not found for: " + slug);

        String normalizedPagePath = normalizePagePath(pagePath);
        if (normalizedPagePath.isBlank()) return ResponseEntity.badRequest().body("Invalid page path");

        try {
            String body = fetchPagePayload(id, normalizedPagePath);
            return ResponseEntity.ok().body(objectMapper.readTree(body));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (RestClientException e) {
            logger.error("Wiki page fetch transport failure for slug '{}' page '{}': {}", slug, pagePath, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Wiki upstream is temporarily unavailable.");
        } catch (Exception e) {
            logger.error("Unexpected wiki page fetch error for slug '{}' page '{}': {}", slug, pagePath, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Failed to fetch wiki page data.");
        }
    }

    private String resolveTargetWikiSlug(String requestedSlug) {
        Optional<Project> optProject = projectRepository.findById(requestedSlug);
        if (optProject.isEmpty()) {
            optProject = projectRepository.findBySlug(requestedSlug.toLowerCase());
        }

        if (optProject.isPresent()) {
            Project project = optProject.get();
            if (project.isHmWikiEnabled() && project.getHmWikiSlug() != null && !project.getHmWikiSlug().isBlank()) {
                return project.getHmWikiSlug().trim();
            }
        }
        return requestedSlug;
    }

    @Cacheable(value = "wikiSlugToId", key = "#hmSlug.toLowerCase()")
    public String resolveWikiModId(String hmSlug) {
        try {
            String body = fetchModsPayload();
            JsonNode root = objectMapper.readTree(body);
            JsonNode list = root.isArray() ? root : (root.has("data") ? root.get("data") : null);
            if (list != null && list.isArray()) {
                for (JsonNode node : list) {
                    if (node.has("slug") && hmSlug.equalsIgnoreCase(node.get("slug").asText()) && node.has("id")) {
                        return node.get("id").asText();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Wiki ID resolution failed for slug: {}", hmSlug, e);
        }
        return null;
    }

    @Cacheable(value = "wikiModsPayload", key = "'mods-list'")
    public String fetchModsPayload() {
        return fetchWithAuthFallback(wikiApiUrl + "/mods");
    }

    @Cacheable(value = "wikiProjectPayload", key = "#id")
    public String fetchProjectPayload(String id) {
        return fetchWithAuthFallback(wikiApiUrl + "/mods/" + id);
    }

    @Cacheable(value = "wikiPagePayload", key = "#id + ':' + #pagePath")
    public String fetchPagePayload(String id, String pagePath) {
        return fetchWithAuthFallback(wikiApiUrl + "/mods/" + id + "/" + pagePath);
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (wikiApiKey != null && !wikiApiKey.isBlank()) {
            headers.setBearerAuth(wikiApiKey);
        }
        return headers;
    }

    private String normalizePagePath(String pagePath) {
        if (pagePath == null) return "";
        String normalized = pagePath.trim();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }

    private String fetchWithAuthFallback(String url) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders()),
                    String.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            HttpStatusCode code = e.getStatusCode();
            boolean authConfigured = wikiApiKey != null && !wikiApiKey.isBlank();
            boolean authRejected = code.value() == 401 || code.value() == 403;

            if (authConfigured && authRejected) {
                logger.warn("Wiki API rejected configured bearer token ({}). Retrying unauthenticated for URL: {}", code, url);
                ResponseEntity<String> retry = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(new HttpHeaders()),
                        String.class
                );
                return retry.getBody();
            }
            throw e;
        }
    }
}
