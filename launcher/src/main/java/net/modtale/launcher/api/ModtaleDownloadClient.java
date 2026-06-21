package net.modtale.launcher.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import net.modtale.launcher.logging.LogSanitizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class ModtaleDownloadClient {

    private static final Logger LOG = LogManager.getLogger(ModtaleDownloadClient.class);

    private final HttpClient httpClient;
    private final Supplier<URI> apiBaseUri;

    ModtaleDownloadClient(HttpClient httpClient, Supplier<URI> apiBaseUri) {
        this.httpClient = httpClient;
        this.apiBaseUri = apiBaseUri;
    }

    ModtaleApiClient.DownloadedFile download(String rawUrl) {
        URI uri = resolve(rawUrl);
        HttpRequest request = ModtaleApiTransport.requestBuilder(uri)
                .GET()
                .header("Accept", "application/octet-stream, application/zip, */*")
                .build();
        try {
            LOG.info("GET " + LogSanitizer.uri(uri));
            Instant started = Instant.now();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            long elapsedMs = Duration.between(started, Instant.now()).toMillis();
            LOG.info("GET " + LogSanitizer.uri(uri) + " -> HTTP "
                    + response.statusCode() + " in " + elapsedMs + "ms");
            ModtaleApiTransport.ensureSuccess(response.statusCode(), uri.toString());
            String filename = filenameFromDisposition(response.headers().firstValue("Content-Disposition"))
                    .or(() -> filenameFromUri(uri))
                    .orElse("download.bin");
            Path tempFile = Files.createTempFile("modtale-", "-" + SafeDownloadName.sanitize(filename));
            try (InputStream body = response.body()) {
                Files.copy(body, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            LOG.info("Saved " + LogSanitizer.uri(uri)
                    + " as " + filename
                    + " contentType=" + response.headers().firstValue("Content-Type").orElse("")
                    + " temp=" + tempFile
                    + " bytes=" + Files.size(tempFile));
            return new ModtaleApiClient.DownloadedFile(
                    tempFile,
                    filename,
                    response.headers().firstValue("Content-Type").orElse("")
            );
        } catch (IOException ex) {
            LOG.warn("Could not download " + LogSanitizer.uri(uri), ex);
            throw new ModtaleApiException("Could not download " + LogSanitizer.uri(uri), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted downloading " + LogSanitizer.uri(uri), ex);
            throw new ModtaleApiException("Download was interrupted.", ex);
        }
    }

    URI resolve(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ModtaleApiException("The API returned an empty download URL.");
        }
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            return URI.create(rawUrl);
        }

        String base = apiBaseUri.get().toString().replaceAll("/+$", "");
        String path = rawUrl.startsWith("/") ? rawUrl : "/" + rawUrl;
        return URI.create(base + path);
    }

    private static Optional<String> filenameFromDisposition(Optional<String> header) {
        return header.flatMap(value -> {
            for (String part : value.split(";")) {
                String trimmed = part.trim();
                if (trimmed.toLowerCase().startsWith("filename=")) {
                    String filename = trimmed.substring("filename=".length()).trim();
                    if (filename.startsWith("\"") && filename.endsWith("\"") && filename.length() >= 2) {
                        filename = filename.substring(1, filename.length() - 1);
                    }
                    if (!filename.isBlank()) {
                        return Optional.of(filename);
                    }
                }
            }
            return Optional.empty();
        });
    }

    private static Optional<String> filenameFromUri(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        int slash = path.lastIndexOf('/');
        String filename = slash >= 0 ? path.substring(slash + 1) : path;
        return filename.isBlank() ? Optional.empty() : Optional.of(filename);
    }

    private static final class SafeDownloadName {
        private SafeDownloadName() {
        }

        private static String sanitize(String value) {
            String sanitized = value == null ? "download.bin" : value.replaceAll("[^A-Za-z0-9._-]+", "-");
            return sanitized.isBlank() ? "download.bin" : sanitized;
        }
    }
}
