package net.modtale.controller.project;

import com.fasterxml.jackson.databind.JsonNode;
import net.modtale.model.project.Project;
import net.modtale.repository.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/wiki")
public class WikiProxyController {

    private static final Logger logger = LoggerFactory.getLogger(WikiProxyController.class);

    @Value("${app.hytalemodding.wiki-key:}") private String wikiApiKey;
    @Value("${app.hytalemodding.wiki-url:https://wiki.hytalemodding.dev/api}") private String wikiApiUrl;

    @Autowired
    private ProjectRepository projectRepository;

    private final Map<String, String> wikiSlugToIdCache = new ConcurrentHashMap<>();

    private String resolveTargetWikiSlug(String requestedSlug) {
        Optional<Project> optProject = projectRepository.findById(requestedSlug);
        if (optProject.isEmpty()) {
            optProject = projectRepository.findBySlug(requestedSlug.toLowerCase());
        }

        if (optProject.isPresent()) {
            Project project = optProject.get();
            if (project.isHmWikiEnabled() && project.getHmWikiSlug() != null && !project.getHmWikiSlug().isBlank()) {
                return project.getHmWikiSlug();
            }
        }
        return requestedSlug;
    }

    private String resolveWikiModId(String hmSlug, RestTemplate restTemplate, HttpHeaders headers) {
        if (wikiSlugToIdCache.containsKey(hmSlug)) return wikiSlugToIdCache.get(hmSlug);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(wikiApiUrl + "/mods", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode root = response.getBody();
            if (root != null) {
                JsonNode list = root.isArray() ? root : (root.has("data") ? root.get("data") : null);
                if (list != null && list.isArray()) {
                    for (JsonNode node : list) {
                        if (node.has("slug") && hmSlug.equalsIgnoreCase(node.get("slug").asText()) && node.has("id")) {
                            wikiSlugToIdCache.put(hmSlug, node.get("id").asText());
                            return node.get("id").asText();
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Wiki ID resolution failed for slug: " + hmSlug, e);
        }
        return null;
    }

    private ResponseEntity<String> cleanProxyResponse(ResponseEntity<String> response) {
        HttpHeaders cleanHeaders = new HttpHeaders();
        response.getHeaders().forEach((key, value) -> {
            if (!key.toLowerCase().startsWith("access-control-")) {
                cleanHeaders.addAll(key, value);
            }
        });
        return ResponseEntity.status(response.getStatusCode()).headers(cleanHeaders).body(response.getBody());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> getWikiProject(@PathVariable String slug) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        if (wikiApiKey != null && !wikiApiKey.isEmpty()) headers.setBearerAuth(wikiApiKey);

        String targetSlug = resolveTargetWikiSlug(slug);
        String id = resolveWikiModId(targetSlug, restTemplate, headers);

        if (id == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wiki not found for: " + slug);

        String targetUrl = wikiApiUrl + "/mods/" + id;
        logger.info("Proxying wiki project request for local slug '{}' (HM ID: '{}') to: {}", slug, id, targetUrl);

        try {
            ResponseEntity<String> response = restTemplate.exchange(targetUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return cleanProxyResponse(response);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @GetMapping("/{slug}/**")
    public ResponseEntity<?> getWikiPage(@PathVariable String slug, HttpServletRequest request) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        if (wikiApiKey != null && !wikiApiKey.isEmpty()) headers.setBearerAuth(wikiApiKey);

        String targetSlug = resolveTargetWikiSlug(slug);
        String id = resolveWikiModId(targetSlug, restTemplate, headers);

        if (id == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wiki not found for: " + slug);

        try {
            String path = request.getRequestURI();
            String searchStr = "/wiki/" + slug + "/";
            int index = path.indexOf(searchStr);
            if (index == -1) return ResponseEntity.badRequest().body("Invalid path");

            String pagePath = path.substring(index + searchStr.length());
            String targetUrl = wikiApiUrl + "/mods/" + id + "/" + pagePath;

            logger.info("Proxying wiki page request for local slug '{}', page '{}' to: {}", slug, pagePath, targetUrl);

            ResponseEntity<String> response = restTemplate.exchange(targetUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return cleanProxyResponse(response);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }
}