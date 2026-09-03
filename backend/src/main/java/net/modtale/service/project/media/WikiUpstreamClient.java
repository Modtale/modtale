package net.modtale.service.project.media;

import java.nio.charset.StandardCharsets;
import net.modtale.config.properties.AppWikiProperties;
import net.modtale.exception.UpstreamServiceException;
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
import org.springframework.web.util.UriUtils;

@Service
public class WikiUpstreamClient {

    private static final Logger logger = LoggerFactory.getLogger(WikiUpstreamClient.class);

    private final AppWikiProperties wikiProperties;
    private final RestTemplate restTemplate;

    public WikiUpstreamClient(
            AppWikiProperties wikiProperties,
            RestTemplate restTemplate
    ) {
        this.wikiProperties = wikiProperties;
        this.restTemplate = restTemplate;
    }

    @Cacheable(value = "wikiProjectPayload", key = "#id", sync = true)
    public String fetchProjectPayload(String id) {
        return fetchWithAuthFallback(wikiProperties.wikiUrl() + "/mods/" + UriUtils.encodePathSegment(id, StandardCharsets.UTF_8));
    }

    @Cacheable(value = "wikiPagePayload", key = "#id + ':' + #pagePath", sync = true)
    public String fetchPagePayload(String id, String pagePath) {
        return fetchWithAuthFallback(wikiProperties.wikiUrl() + "/mods/" + UriUtils.encodePathSegment(id, StandardCharsets.UTF_8) + "/" + UriUtils.encodePath(pagePath, StandardCharsets.UTF_8));
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
