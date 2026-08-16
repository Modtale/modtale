package net.modtale.service.project.version;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.model.dto.project.ExternalProjectReferenceDTO;
import net.modtale.model.project.ProjectDependency;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ExternalProjectReferenceService {

    private static final String CURSEFORGE_HOST = "curseforge.com";
    private static final String GITHUB_HOST = "github.com";
    private static final String GITHUB_RAW_HOST = "raw.githubusercontent.com";
    private static final String GITHUB_CONTENT_HOST = "githubusercontent.com";
    private static final String CF_WIDGET_BASE = "https://api.cfwidget.com/";
    private static final String CURSEFORGE_DOWNLOAD_BASE = "https://www.curseforge.com/api/v1/mods/%s/files/%s/download";

    private final RestTemplate restTemplate;

    public ExternalProjectReferenceService() {
        this(new RestTemplate());
    }

    ExternalProjectReferenceService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ExternalProjectReferenceDTO resolve(String rawUrl, ProjectDependency.Source requestedSource) {
        String externalUrl = trimToNull(rawUrl);
        if (externalUrl == null) {
            throw new InvalidVersionRequestException("External URL is required.");
        }

        ProjectDependency.Source source = requestedSource == null || requestedSource == ProjectDependency.Source.MODTALE
                ? detectSource(externalUrl)
                : requestedSource;

        return switch (source) {
            case CURSEFORGE -> resolveCurseForge(externalUrl);
            case GITHUB -> resolveSimple(externalUrl, ProjectDependency.Source.GITHUB, extractGitHubSlug(externalUrl), false);
            case WEBSITE, OTHER -> resolveSimple(externalUrl, source, extractWebsiteSlug(externalUrl), false);
            case MODTALE -> throw new InvalidVersionRequestException("Use Modtale project search for Modtale dependencies.");
        };
    }

    public String curseForgeDownloadUrl(String projectId, String fileId) {
        if (trimToNull(projectId) == null || trimToNull(fileId) == null) {
            return null;
        }
        return String.format(CURSEFORGE_DOWNLOAD_BASE, projectId.trim(), fileId.trim());
    }

    private ExternalProjectReferenceDTO resolveCurseForge(String externalUrl) {
        CurseForgePath path = parseCurseForgePath(externalUrl);
        if (path == null) {
            throw new InvalidVersionRequestException("CurseForge dependencies must link to a Hytale project or file page.");
        }

        ExternalProjectReferenceDTO fallback = new ExternalProjectReferenceDTO(
                ProjectDependency.Source.CURSEFORGE,
                path.slug(),
                titleFromSlug(path.slug()),
                path.fileId() == null ? "latest" : path.fileId(),
                path.projectUrl(),
                null,
                null,
                true,
                path.fileId() == null ? List.of() : List.of(new ExternalProjectReferenceDTO.ExternalFileDTO(
                        path.fileId(),
                        path.fileId(),
                        null,
                        path.fileId(),
                        null,
                        curseForgeDownloadUrl(path.projectId(), path.fileId())
                ))
        );

        try {
            Map<String, Object> data = getJson(CF_WIDGET_BASE + "hytale/mods/" + path.slug());
            if (data == null || data.isEmpty()) {
                return fallback;
            }

            String projectId = stringValue(data.get("id"));
            List<ExternalProjectReferenceDTO.ExternalFileDTO> files = parseCurseForgeFiles(projectId, data.get("files"));
            ExternalProjectReferenceDTO.ExternalFileDTO selectedFile = files.stream()
                    .filter(file -> path.fileId() == null || path.fileId().equals(file.id()))
                    .findFirst()
                    .orElseGet(() -> parseCurseForgeFile(projectId, data.get("download")));
            String versionNumber = selectedFile != null && trimToNull(selectedFile.versionNumber()) != null
                    ? selectedFile.versionNumber()
                    : "latest";
            String externalId = trimToNull(projectId) != null ? projectId : path.slug();

            return new ExternalProjectReferenceDTO(
                    ProjectDependency.Source.CURSEFORGE,
                    externalId,
                    stringValue(data.get("title"), fallback.title()),
                    versionNumber,
                    path.fileId() == null ? path.projectUrl() : path.fileUrl(),
                    stringValue(data.get("thumbnail")),
                    stringValue(data.get("summary")),
                    true,
                    files
            );
        } catch (RestClientException ex) {
            return fallback;
        }
    }

    private ExternalProjectReferenceDTO resolveSimple(
            String externalUrl,
            ProjectDependency.Source source,
            String externalId,
            boolean hytaleProjectConfirmed
    ) {
        if (!isSecureUrl(externalUrl) || trimToNull(externalId) == null) {
            throw new InvalidVersionRequestException(sourceLabel(source) + " dependencies must use a valid HTTPS URL.");
        }

        return new ExternalProjectReferenceDTO(
                source,
                externalId,
                titleFromSlug(externalId.substring(externalId.lastIndexOf('/') + 1)),
                "latest",
                externalUrl,
                null,
                null,
                hytaleProjectConfirmed,
                externalFilesForUrl(externalUrl)
        );
    }

    private Map<String, Object> getJson(String url) {
        RequestEntity<Void> request = RequestEntity
                .get(URI.create(url))
                .header(HttpHeaders.USER_AGENT, "Modtale/1.0")
                .build();
        return restTemplate.exchange(request, new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
    }

    private List<ExternalProjectReferenceDTO.ExternalFileDTO> parseCurseForgeFiles(String projectId, Object rawFiles) {
        if (!(rawFiles instanceof List<?> files)) {
            return List.of();
        }
        List<ExternalProjectReferenceDTO.ExternalFileDTO> parsed = new ArrayList<>();
        for (Object rawFile : files) {
            ExternalProjectReferenceDTO.ExternalFileDTO file = parseCurseForgeFile(projectId, rawFile);
            if (file != null) {
                parsed.add(file);
            }
        }
        parsed.sort(Comparator.comparing(ExternalProjectReferenceDTO.ExternalFileDTO::id, Comparator.nullsLast(String::compareTo)).reversed());
        return parsed.stream().limit(20).toList();
    }

    @SuppressWarnings("unchecked")
    private ExternalProjectReferenceDTO.ExternalFileDTO parseCurseForgeFile(String projectId, Object rawFile) {
        if (!(rawFile instanceof Map<?, ?> file)) {
            return null;
        }
        String id = stringValue(file.get("id"));
        String fileName = stringValue(file.get("name"));
        String displayName = stringValue(file.get("display"), fileName);
        String version = stringValue(file.get("version"), "latest");
        String type = stringValue(file.get("type"));
        String downloadUrl = trimToNull(projectId) != null && trimToNull(id) != null
                ? curseForgeDownloadUrl(projectId, id)
                : stringValue(file.get("url"));
        return new ExternalProjectReferenceDTO.ExternalFileDTO(id, displayName, fileName, version, type, downloadUrl);
    }

    private ProjectDependency.Source detectSource(String externalUrl) {
        try {
            URI uri = new URI(externalUrl);
            String host = uri.getHost();
            if (host == null) {
                return ProjectDependency.Source.WEBSITE;
            }
            if (isHost(host, CURSEFORGE_HOST)) return ProjectDependency.Source.CURSEFORGE;
            if (isGitHubHost(host)) return ProjectDependency.Source.GITHUB;
            return ProjectDependency.Source.WEBSITE;
        } catch (URISyntaxException ex) {
            return ProjectDependency.Source.WEBSITE;
        }
    }

    private CurseForgePath parseCurseForgePath(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            String[] segments = pathSegments(uri);
            if (host == null || !isHost(host, CURSEFORGE_HOST) || segments.length < 3) {
                return null;
            }
            if (!"hytale".equalsIgnoreCase(segments[0]) || !"mods".equalsIgnoreCase(segments[1])) {
                return null;
            }
            String slug = segments[2];
            String fileId = null;
            for (int i = 0; i < segments.length - 1; i++) {
                if ("files".equalsIgnoreCase(segments[i]) && !segments[i + 1].isBlank()) {
                    fileId = segments[i + 1];
                    break;
                }
            }
            String projectId = trimToNull(uri.getQuery()) == null ? null : extractQueryParam(uri.getQuery(), "projectId");
            String projectUrl = UriComponentsBuilder.fromUri(uri)
                    .replacePath("/hytale/mods/" + slug)
                    .replaceQuery(null)
                    .fragment(null)
                    .build()
                    .toUriString();
            return new CurseForgePath(slug, projectId, fileId, projectUrl, value);
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private String extractQueryParam(String query, String name) {
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            if (name.equals(part.substring(0, equals))) {
                return part.substring(equals + 1);
            }
        }
        return null;
    }

    private String extractGitHubSlug(String value) {
        try {
            URI uri = new URI(value);
            String[] segments = pathSegments(uri);
            if (segments.length < 2) {
                return null;
            }
            return sanitizeExternalId(segments[0] + "/" + segments[1]);
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private String extractWebsiteSlug(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            String[] segments = pathSegments(uri);
            String candidate = segments.length == 0 ? host : host + "/" + segments[segments.length - 1];
            return sanitizeExternalId(candidate);
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private boolean isSecureUrl(String value) {
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private boolean isHost(String host, String expectedHost) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.equals(expectedHost) || normalizedHost.endsWith("." + expectedHost);
    }

    private boolean isGitHubHost(String host) {
        return isHost(host, GITHUB_HOST) || isHost(host, GITHUB_RAW_HOST) || isHost(host, GITHUB_CONTENT_HOST);
    }

    private List<ExternalProjectReferenceDTO.ExternalFileDTO> externalFilesForUrl(String externalUrl) {
        String fileName = fileNameFromUrl(externalUrl);
        if (fileName == null) {
            return List.of();
        }

        return List.of(new ExternalProjectReferenceDTO.ExternalFileDTO(
                "direct",
                fileName,
                fileName,
                "latest",
                null,
                externalUrl
        ));
    }

    private String fileNameFromUrl(String value) {
        try {
            URI uri = new URI(value);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String filename = path.substring(path.lastIndexOf('/') + 1);
            String lower = filename.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".jar") && !lower.endsWith(".zip")) {
                return null;
            }
            return filename;
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private String[] pathSegments(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
    }

    private String sanitizeExternalId(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = value.trim()
                .replaceAll("[^A-Za-z0-9._/-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return sanitized.isBlank() ? null : sanitized;
    }

    private String titleFromSlug(String value) {
        String raw = trimToNull(value);
        if (raw == null) {
            return "External Project";
        }
        String last = raw.substring(raw.lastIndexOf('/') + 1);
        String[] words = last.replace('-', ' ').replace('_', ' ').split("\\s+");
        List<String> titled = new ArrayList<>();
        for (String word : words) {
            if (word.isBlank()) continue;
            titled.add(word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1));
        }
        return titled.isEmpty() ? raw : String.join(" ", titled);
    }

    private String stringValue(Object value) {
        return stringValue(value, null);
    }

    private String stringValue(Object value, String fallback) {
        String string = value == null ? null : value.toString();
        return trimToNull(string) == null ? fallback : string.trim();
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

    private record CurseForgePath(String slug, String projectId, String fileId, String projectUrl, String fileUrl) {}
}
