package net.modtale.launcher.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.modtale.launcher.logging.LogSanitizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class ModtaleApiTransport {

    private static final Logger LOG = LogManager.getLogger(ModtaleApiTransport.class);
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final ApiResponseCache responseCache;
    private final Supplier<Optional<String>> csrfTokenSupplier;

    ModtaleApiTransport(HttpClient httpClient, ApiResponseCache responseCache) {
        this(httpClient, responseCache, Optional::empty);
    }

    ModtaleApiTransport(
            HttpClient httpClient,
            ApiResponseCache responseCache,
            Supplier<Optional<String>> csrfTokenSupplier
    ) {
        this.httpClient = httpClient;
        this.responseCache = responseCache;
        this.csrfTokenSupplier = csrfTokenSupplier == null ? Optional::empty : csrfTokenSupplier;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    <T> T get(URI uri, Class<T> type, Duration cacheTtl) {
        return sendJson(requestBuilder(uri).GET().build(), type, cacheTtl);
    }

    <T> T get(URI uri, TypeReference<T> type, Duration cacheTtl) {
        return sendJson(requestBuilder(uri).GET().build(), type, cacheTtl);
    }

    <T> T post(URI uri, Object payload, Class<T> type) {
        try {
            HttpRequest request = writeRequestBuilder(uri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            return sendJson(request, type, Duration.ZERO);
        } catch (IOException ex) {
            LOG.warn("Could not write POST body for " + LogSanitizer.uri(uri), ex);
            throw new ModtaleApiException("Could not write API request body for " + LogSanitizer.uri(uri), ex);
        }
    }

    <T> T post(URI uri, Object payload, TypeReference<T> type) {
        try {
            HttpRequest request = writeRequestBuilder(uri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            return sendJson(request, type, Duration.ZERO);
        } catch (IOException ex) {
            LOG.warn("Could not write POST body for " + LogSanitizer.uri(uri), ex);
            throw new ModtaleApiException("Could not write API request body for " + LogSanitizer.uri(uri), ex);
        }
    }

    <T> T put(URI uri, Object payload, Class<T> type) {
        try {
            HttpRequest request = writeRequestBuilder(uri)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            return sendJson(request, type);
        } catch (IOException ex) {
            LOG.warn("Could not write PUT body for " + LogSanitizer.uri(uri), ex);
            throw new ModtaleApiException("Could not write API request body for " + LogSanitizer.uri(uri), ex);
        }
    }

    <T> T delete(URI uri, Class<T> type) {
        return sendJson(writeRequestBuilder(uri).DELETE().build(), type);
    }

    void clearResponseCache() {
        responseCache.clear();
    }

    private <T> T sendJson(HttpRequest request, Class<T> type) {
        return sendJson(request, type, Duration.ZERO);
    }

    private <T> T sendJson(HttpRequest request, Class<T> type, Duration cacheTtl) {
        if (ApiCachePolicy.isEnabled(cacheTtl)) {
            Optional<String> cached = responseCache.getFresh(request.uri(), cacheTtl);
            if (cached.isPresent()) {
                try {
                    return mapper.readValue(cached.get(), type);
                } catch (IOException ex) {
                    responseCache.invalidate(request.uri());
                }
            }
        }

        try {
            LOG.info(request.method() + " " + LogSanitizer.uri(request.uri()));
            Instant started = Instant.now();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            logResponse(request, response.statusCode(), response.body(), started);
            ensureSuccess(response.statusCode(), request.uri().toString(), response.body());
            if (ApiCachePolicy.isEnabled(cacheTtl)) {
                responseCache.put(request.uri(), response.body());
            }
            return readResponseBody(response.body(), type);
        } catch (IOException ex) {
            LOG.warn("I/O failure reading " + request.method() + " " + LogSanitizer.uri(request.uri()), ex);
            Optional<T> stale = readStaleFallback(request, type, cacheTtl);
            if (stale.isPresent()) {
                LOG.warn("Using stale cached response for " + LogSanitizer.uri(request.uri()));
                return stale.get();
            }
            throw new ModtaleApiException("Could not read API response from " + LogSanitizer.uri(request.uri()), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while reading " + request.method() + " " + LogSanitizer.uri(request.uri()), ex);
            Optional<T> stale = readStaleFallback(request, type, cacheTtl);
            if (stale.isPresent()) {
                LOG.warn("Using stale cached response for " + LogSanitizer.uri(request.uri()));
                return stale.get();
            }
            throw new ModtaleApiException("API request was interrupted.", ex);
        }
    }

    private <T> T sendJson(HttpRequest request, TypeReference<T> type, Duration cacheTtl) {
        if (ApiCachePolicy.isEnabled(cacheTtl)) {
            Optional<String> cached = responseCache.getFresh(request.uri(), cacheTtl);
            if (cached.isPresent()) {
                try {
                    return mapper.readValue(cached.get(), type);
                } catch (IOException ex) {
                    responseCache.invalidate(request.uri());
                }
            }
        }

        try {
            LOG.info(request.method() + " " + LogSanitizer.uri(request.uri()));
            Instant started = Instant.now();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            logResponse(request, response.statusCode(), response.body(), started);
            ensureSuccess(response.statusCode(), request.uri().toString(), response.body());
            if (ApiCachePolicy.isEnabled(cacheTtl)) {
                responseCache.put(request.uri(), response.body());
            }
            return mapper.readValue(response.body(), type);
        } catch (IOException ex) {
            LOG.warn("I/O failure reading " + request.method() + " " + LogSanitizer.uri(request.uri()), ex);
            Optional<T> stale = readStaleFallback(request, type, cacheTtl);
            if (stale.isPresent()) {
                LOG.warn("Using stale cached response for " + LogSanitizer.uri(request.uri()));
                return stale.get();
            }
            throw new ModtaleApiException("Could not read API response from " + LogSanitizer.uri(request.uri()), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while reading " + request.method() + " " + LogSanitizer.uri(request.uri()), ex);
            Optional<T> stale = readStaleFallback(request, type, cacheTtl);
            if (stale.isPresent()) {
                LOG.warn("Using stale cached response for " + LogSanitizer.uri(request.uri()));
                return stale.get();
            }
            throw new ModtaleApiException("API request was interrupted.", ex);
        }
    }

    private <T> T readResponseBody(String body, Class<T> type) throws IOException {
        if (body == null || body.isBlank()) {
            if (type == Void.class || type == Object.class) {
                return null;
            }
        }
        return mapper.readValue(body, type);
    }

    private <T> Optional<T> readStaleFallback(HttpRequest request, Class<T> type, Duration cacheTtl) {
        if (!ApiCachePolicy.isEnabled(cacheTtl)) {
            return Optional.empty();
        }
        Optional<String> cached = responseCache.getStaleFallback(request.uri());
        if (cached.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(cached.get(), type));
        } catch (IOException ex) {
            responseCache.invalidate(request.uri());
            return Optional.empty();
        }
    }

    private <T> Optional<T> readStaleFallback(HttpRequest request, TypeReference<T> type, Duration cacheTtl) {
        if (!ApiCachePolicy.isEnabled(cacheTtl)) {
            return Optional.empty();
        }
        Optional<String> cached = responseCache.getStaleFallback(request.uri());
        if (cached.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(cached.get(), type));
        } catch (IOException ex) {
            responseCache.invalidate(request.uri());
            return Optional.empty();
        }
    }

    private void logResponse(HttpRequest request, int status, String body, Instant started) {
        long elapsedMs = Duration.between(started, Instant.now()).toMillis();
        String target = LogSanitizer.uri(request.uri());
        if (status >= 200 && status < 300) {
            LOG.info(request.method() + " " + target + " -> HTTP " + status + " in " + elapsedMs + "ms");
            return;
        }

        LOG.warn(request.method() + " " + target + " -> HTTP " + status + " in " + elapsedMs
                + "ms body=" + LogSanitizer.bodyPreview(body));
    }

    static HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                .header("User-Agent", "ModtaleLauncher/0.1");
    }

    private HttpRequest.Builder writeRequestBuilder(URI uri) {
        HttpRequest.Builder builder = requestBuilder(uri);
        csrfTokenSupplier.get()
                .filter(token -> !token.isBlank())
                .ifPresent(token -> builder.header(CSRF_HEADER_NAME, token));
        return builder;
    }

    static void ensureSuccess(int status, String target) {
        if (status < 200 || status >= 300) {
            String safeTarget = LogSanitizer.url(target);
            LOG.warn("HTTP " + status + " for " + safeTarget);
            throw new ModtaleApiException("Modtale API returned HTTP " + status + " for " + safeTarget, status, null);
        }
    }

    private void ensureSuccess(int status, String target, String body) {
        if (status >= 200 && status < 300) {
            return;
        }
        String serverMessage = serverErrorMessage(body);
        if (serverMessage == null || serverMessage.isBlank()) {
            String safeTarget = LogSanitizer.url(target);
            LOG.warn("HTTP " + status + " for " + safeTarget);
            throw new ModtaleApiException("Modtale API returned HTTP " + status + " for " + safeTarget, status, null);
        }
        throw new ModtaleApiException(serverMessage, status, null);
    }

    private String serverErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(body);
            for (String field : List.of("message", "error", "detail", "title")) {
                JsonNode value = root.get(field);
                if (value != null && value.isTextual() && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }
}
