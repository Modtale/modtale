package net.modtale.service.project.version;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.model.dto.request.project.DependencyReferenceRequest;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import net.modtale.model.project.ProjectStatus;
import net.modtale.service.project.query.ProjectService;
import org.springframework.stereotype.Service;

@Service
public class VersionDependencyService {

    private static final String CURSEFORGE_HOST = "curseforge.com";
    private static final String GITHUB_HOST = "github.com";
    private static final String GITHUB_RAW_HOST = "raw.githubusercontent.com";
    private static final String GITHUB_CONTENT_HOST = "githubusercontent.com";

    private final ProjectService projectService;

    public VersionDependencyService(ProjectService projectService) {
        this.projectService = projectService;
    }

    public ResolvedDependencies resolveRequestedDependencies(
            List<DependencyReferenceRequest> dependencyReferences,
            boolean isModpack,
            boolean allowDraftDependencies
    ) {
        if (dependencyReferences == null) {
            return new ResolvedDependencies(List.of(), List.of());
        }

        List<ProjectDependency> dependencies = new ArrayList<>();
        List<String> simpleProjectIds = new ArrayList<>();

        for (DependencyReferenceRequest reference : dependencyReferences) {
            if (reference == null) {
                continue;
            }

            ProjectDependency.Source source = reference.getSource();
            ProjectDependency dependency = source == ProjectDependency.Source.MODTALE
                    ? resolveModtaleDependency(reference, isModpack, allowDraftDependencies)
                    : resolveExternalDependency(reference, source, isModpack);
            dependencies.add(dependency);
            if (!dependency.isExternal()) {
                simpleProjectIds.add(dependency.getProjectId());
            }
        }

        if (isModpack && dependencies.size() < 2) {
            throw new InvalidVersionRequestException("Modpacks must include at least two dependencies.");
        }

        return new ResolvedDependencies(dependencies, simpleProjectIds);
    }

    public List<String> resolveRequestedProjectIds(
            List<String> projectIds,
            boolean allowDraftProjects
    ) {
        if (projectIds == null) {
            return List.of();
        }

        List<String> resolvedProjectIds = new ArrayList<>();
        for (String entry : projectIds) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            String projectId = entry.trim();
            Project project = projectService.getRawProjectById(projectId);
            if (project == null || (!allowDraftProjects && project.getStatus() == ProjectStatus.DRAFT)) {
                throw new InvalidVersionRequestException("One or more selected incompatible mods could not be found.");
            }
            resolvedProjectIds.add(project.getId());
        }

        return resolvedProjectIds;
    }

    private ProjectDependency resolveModtaleDependency(
            DependencyReferenceRequest reference,
            boolean isModpack,
            boolean allowDraftDependencies
    ) {
        String projectId = trimToNull(reference.getProjectId());
        String versionNumber = trimToNull(reference.getVersionNumber());
        if (projectId == null || versionNumber == null) {
            throw new InvalidVersionRequestException("Modtale dependencies require a project and version.");
        }

        Project dependencyProject = projectService.getRawProjectById(projectId);
        if (dependencyProject == null
                || (!allowDraftDependencies && dependencyProject.getStatus() == ProjectStatus.DRAFT)
                || dependencyProject.getVersions() == null
                || dependencyProject.getVersions().stream().noneMatch(version ->
                version.getVersionNumber() != null && version.getVersionNumber().equalsIgnoreCase(versionNumber))) {
            throw new InvalidVersionRequestException("One or more selected dependencies could not be found.");
        }

        ProjectDependency.DependencyType dependencyType = isModpack
                ? ProjectDependency.DependencyType.REQUIRED
                : reference.getDependencyType();
        ProjectDependency dependency = ProjectDependency.modtale(
                dependencyProject.getId(),
                dependencyProject.getTitle(),
                versionNumber,
                dependencyType
        );
        dependency.setId(reference.getId());
        return dependency;
    }

    private ProjectDependency resolveExternalDependency(
            DependencyReferenceRequest reference,
            ProjectDependency.Source source,
            boolean isModpack
    ) {
        String externalUrl = trimToNull(reference.getExternalUrl());
        String title = trimToNull(reference.getProjectTitle());
        String versionNumber = trimToNull(reference.getVersionNumber());
        if (externalUrl == null || title == null || versionNumber == null) {
            throw new InvalidVersionRequestException("External dependencies require a title, version, and URL.");
        }

        String externalId = trimToNull(reference.getExternalId());
        if (externalId == null) {
            externalId = extractExternalId(source, externalUrl);
        }

        if (externalId == null || !isValidExternalUrl(source, externalUrl)) {
            throw new InvalidVersionRequestException(sourceLabel(source) + " dependencies must use a valid project URL.");
        }

        boolean hytaleProjectConfirmed = isVerifiedHytaleUrl(source, externalUrl) || reference.isHytaleProjectConfirmed();
        if (!hytaleProjectConfirmed) {
            throw new InvalidVersionRequestException("External dependencies must be confirmed as Hytale-compatible projects.");
        }

        ProjectDependency.DependencyType dependencyType = isModpack
                ? ProjectDependency.DependencyType.REQUIRED
                : reference.getDependencyType();
        ProjectDependency dependency = ProjectDependency.external(source, externalId, title, versionNumber, externalUrl, dependencyType);
        dependency.setId(reference.getId());
        dependency.setExternalFileUrl(trimToNull(reference.getExternalFileUrl()));
        dependency.setExternalFileName(trimToNull(reference.getExternalFileName()));
        dependency.setHytaleProjectConfirmed(hytaleProjectConfirmed);
        return dependency;
    }

    private boolean isValidExternalUrl(ProjectDependency.Source source, String value) {
        return switch (source) {
            case CURSEFORGE -> isHytaleCurseForgeModUrl(value);
            case GITHUB -> isGitHubProjectUrl(value);
            case WEBSITE, OTHER -> isSecureUrl(value);
            case MODTALE -> false;
        };
    }

    private boolean isVerifiedHytaleUrl(ProjectDependency.Source source, String value) {
        return source == ProjectDependency.Source.CURSEFORGE && isHytaleCurseForgeModUrl(value);
    }

    private boolean isHytaleCurseForgeModUrl(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            String path = uri.getPath();
            return host != null
                    && (host.equalsIgnoreCase(CURSEFORGE_HOST) || host.toLowerCase().endsWith("." + CURSEFORGE_HOST))
                    && path != null
                    && path.toLowerCase().startsWith("/hytale/")
                    && path.toLowerCase().contains("/mods/")
                    && extractCurseForgeSlug(value) != null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private boolean isGitHubProjectUrl(String value) {
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            String[] segments = pathSegments(uri);
            return host != null && isGitHubHost(host) && segments.length >= 2;
        } catch (URISyntaxException ex) {
            return false;
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

    private String extractExternalId(ProjectDependency.Source source, String value) {
        return switch (source) {
            case CURSEFORGE -> extractCurseForgeSlug(value);
            case GITHUB -> extractGitHubSlug(value);
            case WEBSITE, OTHER -> extractWebsiteSlug(value);
            case MODTALE -> null;
        };
    }

    private String extractCurseForgeSlug(String value) {
        try {
            URI uri = new URI(value);
            String path = uri.getPath();
            if (path == null) {
                return null;
            }
            String[] segments = path.split("/");
            for (int i = 0; i < segments.length - 1; i++) {
                if ("mods".equalsIgnoreCase(segments[i]) && !segments[i + 1].isBlank()) {
                    return segments[i + 1].trim();
                }
            }
            return null;
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private String extractGitHubSlug(String value) {
        try {
            String[] segments = pathSegments(new URI(value));
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

    public record ResolvedDependencies(List<ProjectDependency> dependencies, List<String> simpleProjectIds) {
    }
}
