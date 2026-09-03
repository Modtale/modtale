package net.modtale.service.project.version;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.model.dto.project.ExternalProjectReferenceDTO;
import net.modtale.model.project.ProjectDependency;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ExternalProjectReferenceService {

    private static final String CURSEFORGE_HOST = "curseforge.com";
    private static final String GITHUB_HOST = "github.com";
    private static final String GITHUB_RAW_HOST = "raw.githubusercontent.com";
    private static final String GITHUB_CONTENT_HOST = "githubusercontent.com";

    private final CurseForgeApiClient curseForgeApiClient;

    public ExternalProjectReferenceService(CurseForgeApiClient curseForgeApiClient) {
        this.curseForgeApiClient = curseForgeApiClient;
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

    private ExternalProjectReferenceDTO resolveCurseForge(String externalUrl) {
        CurseForgePath path = parseCurseForgePath(externalUrl);
        if (path == null) {
            throw new InvalidVersionRequestException("CurseForge dependencies must link to a Hytale project or file page.");
        }

        ExternalProjectReferenceDTO fallback = new ExternalProjectReferenceDTO(
                ProjectDependency.Source.CURSEFORGE,
                path.projectId() == null ? path.slug() : path.projectId(),
                titleFromSlug(path.slug()),
                path.fileId() == null ? "latest" : path.fileId(),
                path.fileId() == null ? path.projectUrl() : path.fileUrl(),
                null,
                null,
                true,
                path.fileId() == null ? List.of() : List.of(new ExternalProjectReferenceDTO.ExternalFileDTO(
                        path.fileId(),
                        path.fileId(),
                        null,
                        path.fileId(),
                        null,
                        null
                ))
        );
        return curseForgeApiClient.resolveProject(path.slug(), path.fileId())
                .map(project -> new ExternalProjectReferenceDTO(
                        ProjectDependency.Source.CURSEFORGE,
                        project.id(),
                        project.title() == null ? fallback.title() : project.title(),
                        selectedVersion(project.files(), path.fileId(), fallback.versionNumber()),
                        fallback.externalUrl(),
                        project.iconUrl(),
                        project.summary(),
                        true,
                        project.files().stream().map(file -> new ExternalProjectReferenceDTO.ExternalFileDTO(
                                file.id(), file.displayName(), file.fileName(), file.versionNumber(), file.releaseType(), null
                        )).toList()
                ))
                .orElse(fallback);
    }

    private String selectedVersion(List<CurseForgeApiClient.CurseForgeFile> files, String fileId, String fallback) {
        if (fileId == null) {
            return files.isEmpty() ? fallback : files.getFirst().versionNumber();
        }
        return files.stream()
                .filter(file -> fileId.equals(file.id()))
                .map(CurseForgeApiClient.CurseForgeFile::versionNumber)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(fallback);
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
            if (!isSafeCurseForgeUri(uri) || segments.length < 3) {
                return null;
            }
            if (!"hytale".equalsIgnoreCase(segments[0]) || !"mods".equalsIgnoreCase(segments[1])) {
                return null;
            }
            String slug = segments[2];
            if (!slug.matches("[A-Za-z0-9][A-Za-z0-9_-]*")) {
                return null;
            }
            String fileId = null;
            for (int i = 0; i < segments.length - 1; i++) {
                if ("files".equalsIgnoreCase(segments[i]) && !segments[i + 1].isBlank()) {
                    fileId = segments[i + 1];
                    break;
                }
            }
            if (fileId != null && !fileId.matches("\\d+")) {
                return null;
            }
            String projectId = trimToNull(uri.getQuery()) == null ? null : extractQueryParam(uri.getQuery(), "projectId");
            String projectUrl = UriComponentsBuilder.fromUri(uri)
                    .replacePath("/hytale/mods/" + slug)
                    .replaceQuery(null)
                    .fragment(null)
                    .build()
                    .toUriString();
            String fileUrl = fileId == null ? projectUrl : UriComponentsBuilder.fromUri(uri)
                    .replacePath("/hytale/mods/" + slug + "/files/" + fileId)
                    .replaceQuery(null)
                    .fragment(null)
                    .build()
                    .toUriString();
            return new CurseForgePath(slug, projectId, fileId, projectUrl, fileUrl);
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

    private boolean isSafeCurseForgeUri(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getRawUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && isHost(uri.getHost(), CURSEFORGE_HOST);
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
