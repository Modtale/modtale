package net.modtale.controller.wiki;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/wiki")
public class WikiProxyController {

    private static final Logger logger = LoggerFactory.getLogger(WikiProxyController.class);

    @Value("${app.hytalemodding.wiki-key:}") private String wikiApiKey;
    @Value("${app.hytalemodding.wiki-url:https://wiki.hytalemodding.dev/api}") private String wikiApiUrl;

    private final Map<String, String> wikiSlugToIdCache = new ConcurrentHashMap<>();

    private String resolveWikiModId(String slug, RestTemplate restTemplate, HttpHeaders headers) {
        if (wikiSlugToIdCache.containsKey(slug)) return wikiSlugToIdCache.get(slug);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(wikiApiUrl + "/mods", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode root = response.getBody();
            if (root != null) {
                JsonNode list = root.isArray() ? root : (root.has("data") ? root.get("data") : null);
                if (list != null && list.isArray()) {
                    for (JsonNode node : list) {
                        if (node.has("slug") && slug.equalsIgnoreCase(node.get("slug").asText()) && node.has("id")) {
                            wikiSlugToIdCache.put(slug, node.get("id").asText());
                            return node.get("id").asText();
                        }
                    }
                }
            }
        } catch (Exception e) { logger.error("Wiki ID resolution failed for slug: " + slug, e); }
        return null;
    }

    @GetMapping("/{slug}")
    public ResponseEntity<?> getWikiProject(@PathVariable String slug) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        if (wikiApiKey != null && !wikiApiKey.isEmpty()) headers.setBearerAuth(wikiApiKey);

        String id = resolveWikiModId(slug, restTemplate, headers);
        if (id == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wiki not found for: " + slug);

        try {
            return restTemplate.exchange(wikiApiUrl + "/mods/" + id, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        } catch (HttpStatusCodeException e) { return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString()); }
    }

    @GetMapping("/{slug}/**")
    public ResponseEntity<?> getWikiPage(@PathVariable String slug, HttpServletRequest request) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        if (wikiApiKey != null && !wikiApiKey.isEmpty()) headers.setBearerAuth(wikiApiKey);

        String id = resolveWikiModId(slug, restTemplate, headers);
        if (id == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wiki not found for: " + slug);

        try {
            String path = request.getRequestURI();
            String searchStr = "/wiki/" + slug + "/";
            int index = path.indexOf(searchStr);
            if (index == -1) return ResponseEntity.badRequest().body("Invalid path");

            return restTemplate.exchange(wikiApiUrl + "/mods/" + id + "/" + path.substring(index + searchStr.length()), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        } catch (HttpStatusCodeException e) { return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString()); }
    }
}