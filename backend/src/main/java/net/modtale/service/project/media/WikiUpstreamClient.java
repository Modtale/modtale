package net.modtale.service.project.media;

import net.modtale.config.properties.AppWikiProperties;
import net.modtale.exception.UpstreamServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class WikiUpstreamClient {

    private static final Logger logger = LoggerFactory.getLogger(WikiUpstreamClient.class);

    private final ObjectMapper objectMapper;
    private final AppWikiProperties wikiProperties;
    private final RestTemplate restTemplate;
    private final CacheManager cacheManager;

    public WikiUpstreamClient(
            ObjectMapper objectMapper,
            AppWikiProperties wikiProperties,
            RestTemplate restTemplate,
            CacheManager cacheManager
    ) {
        this.objectMapper = objectMapper;
        this.wikiProperties = wikiProperties;
        this.restTemplate = restTemplate;
        this.cacheManager = cacheManager;
    }

    @Cacheable(value = "wikiSlugToId", key = "#hmSlug.toLowerCase()", sync = true)
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

    @Cacheable(value = "wikiProjectPayload", key = "#id", sync = true)
    public String fetchProjectPayload(String id) {
        return fetchWithAuthFallback(wikiProperties.wikiUrl() + "/mods/" + id);
    }

    @Cacheable(value = "wikiPagePayload", key = "#id + ':' + #pagePath", sync = true)
    public String fetchPagePayload(String id, String pagePath) {
        return fetchWithAuthFallback(wikiProperties.wikiUrl() + "/mods/" + id + "/" + pagePath);
    }

    private String fetchModsPayload() {
        Cache cache = cacheManager.getCache("wikiModsPayload");
        if (cache == null) {
            return fetchWithAuthFallback(wikiProperties.wikiUrl() + "/mods");
        }

        return cache.get("mods-list", () -> fetchWithAuthFallback(wikiProperties.wikiUrl() + "/mods"));
    }

    private JsonNode readJson(String body, String failureMessage) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            logger.error("Wiki JSON parse failure: {}", e.getMessage(), e);
            throw new UpstreamServiceException(HttpStatus.BAD_GATEWAY, failureMessage, e);
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (wikiProperties.wikiKey() != null && !wikiProperties.wikiKey().isBlank()) {
            headers.setBearerAuth(wikiProperties.wikiKey());
        }
        return headers;
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
