package net.modtale.service.project.version;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.exception.StorageDownloadException;
import net.modtale.exception.StorageUploadException;
import net.modtale.model.project.ProjectDependency;
import net.modtale.service.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ExternalDependencyArtifactService {

    private static final long MAX_EXTERNAL_FILE_BYTES = 100L * 1024 * 1024;
    private static final int MAX_REDIRECTS = 6;
    private static final String USER_AGENT = "Modtale/1.0";
    private static final String CURSEFORGE_DOWNLOAD_BASE = "https://www.curseforge.com/api/v1/mods/%s/files/%s/download";
    private static final Pattern CURSEFORGE_DOWNLOAD_PATH =
            Pattern.compile(".*/api/v1/mods/(\\d+)/files/(\\d+)/download/?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CURSEFORGE_FILE_PAGE_PATH =
            Pattern.compile(".*/files/(\\d+)/?.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORGECDN_FILE_PATH =
            Pattern.compile(".*/files/(\\d+)/(\\d+)/[^/]+", Pattern.CASE_INSENSITIVE);

    private final StorageService storageService;
    private final HttpClient httpClient;

    @Autowired
    public ExternalDependencyArtifactService(StorageService storageService) {
        this(
                storageService,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()
        );
    }

    ExternalDependencyArtifactService(StorageService storageService, HttpClient httpClient) {
        this.storageService = storageService;
        this.httpClient = httpClient;
    }

    public void prepareExternalArtifacts(List<ProjectDependency> dependencies) {
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }

        for (ProjectDependency dependency : dependencies) {
            if (dependency == null || !dependency.isExternal()) {
                continue;
            }
            prepareExternalArtifact(dependency);
        }
    }

    private void prepareExternalArtifact(ProjectDependency dependency) {
        String fileUrl = trimToNull(dependency.getExternalFileUrl());
        if (fileUrl == null && isDownloadableArchiveUrl(dependency.getExternalUrl())) {
            fileUrl = dependency.getExternalUrl().trim();
            dependency.setExternalFileUrl(fileUrl);
        }

        if (dependency.getSource() == ProjectDependency.Source.CURSEFORGE) {
            cacheCurseForgeDependency(dependency, fileUrl);
            return;
        }

        if (fileUrl != null) {
            validateExternalFileLink(dependency.getSource(), fileUrl);
            dependency.setExternalFileUrl(fileUrl);
            if (trimToNull(dependency.getExternalFileName()) == null) {
                dependency.setExternalFileName(fileNameFromUrl(fileUrl, "external-dependency.jar"));
            }
        }
    }

    private void cacheCurseForgeDependency(ProjectDependency dependency, String fileUrl) {
        CurseForgeFileReference reference = resolveCurseForgeFileReference(dependency, fileUrl);
        if (reference == null) {
            throw new InvalidVersionRequestException("CurseForge dependencies must include a downloadable file so Modtale can cache it.");
        }

        validateInitialSourceUrl(ProjectDependency.Source.CURSEFORGE, reference.downloadUrl());
        String filename = trimToNull(dependency.getExternalFileName());
        if (filename == null) {
            filename = reference.fileName();
        }
        filename = sanitizeArchiveFilename(filename == null ? "curseforge-" + reference.modId() + "-" + reference.fileId() + ".jar" : filename);
        String storageKey = "external-dependencies/curseforge/%s/%s/%s".formatted(reference.modId(), reference.fileId(), filename);

        if (isAlreadyCached(storageKey)) {
            dependency.setExternalFileUrl(reference.downloadUrl());
            dependency.setExternalFileName(filename);
            dependency.setCachedFileUrl(storageKey);
            return;
        }

        DownloadedFile downloaded = downloadFile(reference.downloadUrl(), true);
        validateArchiveSignature(downloaded.bytes(), "CurseForge dependency");
        if (downloaded.bytes().length > MAX_EXTERNAL_FILE_BYTES) {
            throw new InvalidVersionRequestException("CurseForge dependency files must be 100MB or smaller.");
        }

        try {
            storageService.uploadDirect(storageKey, downloaded.bytes(), contentTypeForFilename(filename));
            dependency.setExternalFileUrl(reference.downloadUrl());
            dependency.setExternalFileName(filename);
            dependency.setCachedFileUrl(storageKey);
        } catch (StorageUploadException ex) {
            throw new InvalidVersionRequestException("Could not cache the CurseForge dependency file. Try again later.");
        }
    }

    private void validateExternalFileLink(ProjectDependency.Source source, String fileUrl) {
        if (!isDownloadableArchiveUrl(fileUrl)) {
            throw new InvalidVersionRequestException(sourceLabel(source) + " dependency files must link directly to a .jar or .zip file.");
        }

        validateInitialSourceUrl(source, fileUrl);
        DownloadedFile sample = downloadFile(fileUrl, false);
        validateArchiveSignature(sample.bytes(), sourceLabel(source) + " dependency file");
    }

    private DownloadedFile downloadFile(String rawUrl, boolean fullFile) {
        URI uri = requirePublicHttpsUri(rawUrl);
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", USER_AGENT)
                    .GET();
            if (!fullFile) {
                requestBuilder.header("Range", "bytes=0-3");
            }

            try {
                HttpResponse<InputStream> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    int status = response.statusCode();
                    if (isRedirect(status)) {
                        String location = response.headers().firstValue("location")
                                .orElseThrow(() -> new InvalidVersionRequestException("External dependency file redirect was missing a destination."));
                        uri = requirePublicHttpsUri(uri.resolve(location).toString());
                        continue;
                    }

                    if (status < 200 || status >= 300) {
                        throw new InvalidVersionRequestException("External dependency file could not be downloaded for validation.");
                    }

                    OptionalLong declaredLength = declaredContentLength(response);
                    if (declaredLength.isPresent() && declaredLength.getAsLong() > MAX_EXTERNAL_FILE_BYTES) {
                        throw new InvalidVersionRequestException("External dependency files must be 100MB or smaller.");
                    }

                    long limit = fullFile ? MAX_EXTERNAL_FILE_BYTES + 1 : 4;
                    byte[] bytes = readLimited(body, limit);
                    return new DownloadedFile(bytes, response.headers().firstValue("content-type").orElse("application/octet-stream"), uri);
                }
            } catch (IOException ex) {
                throw new InvalidVersionRequestException("External dependency file could not be downloaded for validation.");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new InvalidVersionRequestException("External dependency file validation was interrupted.");
            }
        }

        throw new InvalidVersionRequestException("External dependency file followed too many redirects.");
    }

    private void validateInitialSourceUrl(ProjectDependency.Source source, String rawUrl) {
        URI uri = requirePublicHttpsUri(rawUrl);
        String host = uri.getHost();
        boolean valid = switch (source) {
            case CURSEFORGE -> isHost(host, "curseforge.com") || isHost(host, "forgecdn.net") || isHost(host, "mediafilez.forgecdn.net");
            case GITHUB -> isHost(host, "github.com") || isHost(host, "raw.githubusercontent.com") || isHost(host, "githubusercontent.com");
            case WEBSITE, OTHER -> true;
            case MODTALE -> false;
        };
        if (!valid) {
            throw new InvalidVersionRequestException(sourceLabel(source) + " dependency files must use a matching external service URL.");
        }
    }

    private CurseForgeFileReference resolveCurseForgeFileReference(ProjectDependency dependency, String fileUrl) {
        String modId = numericOrNull(dependency.getExternalId());
        String fileId = null;
        String filename = trimToNull(dependency.getExternalFileName());

        CurseForgeDownloadPath parsedDownload = parseCurseForgeDownloadPath(fileUrl);
        if (parsedDownload != null) {
            modId = modId == null ? parsedDownload.modId() : modId;
            fileId = parsedDownload.fileId();
        }

        if (fileId == null) {
            fileId = extractCurseForgeFileId(fileUrl);
        }
        if (fileId == null) {
            fileId = extractCurseForgeFileId(dependency.getExternalUrl());
        }
        if (fileId == null) {
            fileId = extractForgeCdnFileId(fileUrl);
        }
        if (filename == null) {
            filename = fileNameFromUrl(fileUrl, null);
        }

        if (modId == null || fileId == null) {
            return null;
        }
        return new CurseForgeFileReference(modId, fileId, String.format(CURSEFORGE_DOWNLOAD_BASE, modId, fileId), filename);
    }

    private CurseForgeDownloadPath parseCurseForgeDownloadPath(String value) {
        URI uri = parseUri(value);
        if (uri == null || uri.getPath() == null) {
            return null;
        }
        Matcher matcher = CURSEFORGE_DOWNLOAD_PATH.matcher(uri.getPath());
        if (!matcher.matches()) {
            return null;
        }
        return new CurseForgeDownloadPath(matcher.group(1), matcher.group(2));
    }

    private String extractCurseForgeFileId(String value) {
        URI uri = parseUri(value);
        if (uri == null || uri.getPath() == null) {
            return null;
        }
        Matcher matcher = CURSEFORGE_FILE_PAGE_PATH.matcher(uri.getPath());
        return matcher.matches() ? numericOrNull(matcher.group(1)) : null;
    }

    private String extractForgeCdnFileId(String value) {
        URI uri = parseUri(value);
        if (uri == null || uri.getPath() == null) {
            return null;
        }
        Matcher matcher = FORGECDN_FILE_PATH.matcher(uri.getPath());
        if (!matcher.matches()) {
            return null;
        }
        return numericOrNull(matcher.group(1) + matcher.group(2));
    }

    private boolean isAlreadyCached(String storageKey) {
        try {
            return storageService.exists(storageKey);
        } catch (StorageDownloadException ex) {
            return false;
        }
    }

    private URI requirePublicHttpsUri(String value) {
        try {
            URI uri = new URI(value).normalize();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new InvalidVersionRequestException("External dependency files must use a valid HTTPS URL.");
            }
            validatePublicHost(uri.getHost());
            return uri;
        } catch (URISyntaxException ex) {
            throw new InvalidVersionRequestException("External dependency files must use a valid HTTPS URL.");
        }
    }

    private void validatePublicHost(String rawHost) {
        String host = IDN.toASCII(rawHost).toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw new InvalidVersionRequestException("External dependency files must use a public HTTPS host.");
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new InvalidVersionRequestException("External dependency files must use a public HTTPS host.");
                }
            }
        } catch (UnknownHostException ex) {
            throw new InvalidVersionRequestException("External dependency file host could not be resolved.");
        }
    }

    private OptionalLong declaredContentLength(HttpResponse<?> response) {
        OptionalLong contentLength = parseLongHeader(response.headers().firstValue("content-length").orElse(null));
        if (contentLength.isPresent()) {
            return contentLength;
        }

        String contentRange = response.headers().firstValue("content-range").orElse(null);
        if (contentRange == null) {
            return OptionalLong.empty();
        }
        int slash = contentRange.lastIndexOf('/');
        if (slash < 0 || slash == contentRange.length() - 1 || "*".equals(contentRange.substring(slash + 1))) {
            return OptionalLong.empty();
        }
        return parseLongHeader(contentRange.substring(slash + 1));
    }

    private OptionalLong parseLongHeader(String value) {
        if (value == null || value.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(value.trim()));
        } catch (NumberFormatException ex) {
            return OptionalLong.empty();
        }
    }

    private byte[] readLimited(InputStream stream, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while (total < limit && (read = stream.read(buffer, 0, (int) Math.min(buffer.length, limit - total))) != -1) {
            output.write(buffer, 0, read);
            total += read;
        }
        return output.toByteArray();
    }

    private void validateArchiveSignature(byte[] bytes, String label) {
        if (bytes == null || bytes.length < 2 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new InvalidVersionRequestException(label + " must be a valid .jar or .zip archive.");
        }
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private boolean isDownloadableArchiveUrl(String value) {
        String filename = fileNameFromUrl(value, null);
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".zip");
    }

    private String fileNameFromUrl(String value, String fallback) {
        URI uri = parseUri(value);
        if (uri == null || uri.getPath() == null) {
            return fallback;
        }
        String path = uri.getPath();
        int slash = path.lastIndexOf('/');
        String filename = slash >= 0 ? path.substring(slash + 1) : path;
        filename = trimToNull(filename);
        return filename == null ? fallback : sanitizeArchiveFilename(filename);
    }

    private String sanitizeArchiveFilename(String filename) {
        String sanitized = filename.replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        if (sanitized.isBlank()) {
            return "external-dependency.jar";
        }
        String lower = sanitized.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".zip") ? sanitized : sanitized + ".jar";
    }

    private String contentTypeForFilename(String filename) {
        return filename.toLowerCase(Locale.ROOT).endsWith(".zip") ? "application/zip" : "application/java-archive";
    }

    private URI parseUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new URI(value.trim());
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private boolean isHost(String host, String expectedHost) {
        if (host == null) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.equals(expectedHost) || normalizedHost.endsWith("." + expectedHost);
    }

    private String numericOrNull(String value) {
        if (value == null || !value.matches("\\d+")) {
            return null;
        }
        return value;
    }

    private String sourceLabel(ProjectDependency.Source source) {
        return switch (source) {
            case CURSEFORGE -> "CurseForge";
            case GITHUB -> "GitHub";
            case WEBSITE -> "Website";
            case OTHER -> "External";
            case MODTALE -> "Modtale";
        };
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record CurseForgeDownloadPath(String modId, String fileId) {}
    private record CurseForgeFileReference(String modId, String fileId, String downloadUrl, String fileName) {}
    private record DownloadedFile(byte[] bytes, String contentType, URI finalUri) {}
}
