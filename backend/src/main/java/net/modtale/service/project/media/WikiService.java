package net.modtale.service.project.media;

import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.exception.UpstreamServiceException;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.service.project.query.ProjectService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class WikiService {

    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final WikiUpstreamClient wikiUpstreamClient;

    public WikiService(
            ProjectService projectService,
            ObjectMapper objectMapper,
            WikiUpstreamClient wikiUpstreamClient
    ) {
        this.projectService = projectService;
        this.objectMapper = objectMapper;
        this.wikiUpstreamClient = wikiUpstreamClient;
    }

    @Cacheable(value = "wikiProjectJson", key = "'public:' + #projectId.toLowerCase()", condition = "#currentUser == null", sync = true)
    public String getWikiProject(String projectId, User currentUser) {
        return loadWikiProject(projectId, currentUser);
    }

    private String loadWikiProject(String projectId, User currentUser) {
        Project project = resolveProjectWikiProject(projectId, currentUser);
        String targetSlug = project.getHmWikiSlug().trim();
        return loadWikiProjectBySlug(targetSlug);
    }

    private String loadWikiProjectBySlug(String targetSlug) {
        return wikiUpstreamClient.fetchProjectPayload(targetSlug);
    }

    @Cacheable(
            value = "wikiPageJson",
            key = "'public:' + #projectId.toLowerCase() + ':' + (#pagePath == null ? '' : #pagePath.trim().toLowerCase())",
            condition = "#currentUser == null",
            sync = true
    )
    public String getWikiPage(String projectId, String pagePath, User currentUser) {
        return loadWikiPage(projectId, pagePath, currentUser);
    }

    private String loadWikiPage(String projectId, String pagePath, User currentUser) {
        String requestedPagePath = normalizePagePath(pagePath);
        Project project = resolveProjectWikiProject(projectId, currentUser);
        String targetSlug = project.getHmWikiSlug().trim();
        String metadataBody = loadWikiProjectBySlug(targetSlug);
        JsonNode metadata = readJson(metadataBody, "Failed to parse wiki project data.");
        String normalizedPagePath = resolvePagePath(requestedPagePath, metadata);
        String wikiId = extractWikiId(metadata, projectId);

        return wikiUpstreamClient.fetchPagePayload(wikiId, normalizedPagePath);
    }

    @Cacheable(
            value = "wikiPageBundleJson",
            key = "'public:' + #projectId.toLowerCase() + ':' + (#pagePath == null ? '' : #pagePath.trim().toLowerCase())",
            condition = "#currentUser == null",
            sync = true
    )
    public String getWikiPageBundle(String projectId, String pagePath, User currentUser) {
        return loadWikiPageBundle(projectId, pagePath, currentUser);
    }

    private String loadWikiPageBundle(String projectId, String pagePath, User currentUser) {
        String requestedPagePath = normalizePagePath(pagePath);
        Project project = resolveProjectWikiProject(projectId, currentUser);
        String targetSlug = project.getHmWikiSlug().trim();
        String metadataBody = loadWikiProjectBySlug(targetSlug);
        JsonNode metadata = readJson(metadataBody, "Failed to parse wiki project data.");
        String normalizedPagePath = resolvePagePath(requestedPagePath, metadata);
        String wikiId = extractWikiId(metadata, projectId);
        String pageBody = wikiUpstreamClient.fetchPagePayload(wikiId, normalizedPagePath);
        JsonNode page = readJson(pageBody, "Failed to parse wiki page data.");

        return buildBundle(project, metadata, page, normalizedPagePath);
    }

    private String buildBundle(Project project, JsonNode metadata, JsonNode page, String normalizedPagePath) {
        ObjectNode bundle = objectMapper.createObjectNode();
        bundle.set("project", projectPageNode(project));
        bundle.set("metadata", metadata);
        bundle.set("page", page);
        bundle.put("pageSlug", normalizedPagePath);

        try {
            return objectMapper.writeValueAsString(bundle);
        } catch (JacksonException e) {
            throw new UpstreamServiceException(HttpStatus.BAD_GATEWAY, "Failed to serialize wiki page data.", e);
        }
    }

    private JsonNode projectPageNode(Project project) {
        try {
            return objectMapper.readTree(objectMapper.writeValueAsString(ProjectMapper.toPageDTO(project)));
        } catch (JacksonException e) {
            throw new UpstreamServiceException(HttpStatus.BAD_GATEWAY, "Failed to serialize project page data.", e);
        }
    }

    private Project resolveProjectWikiProject(String projectId, User currentUser) {
        Project project = projectService.getProjectPageShellByRouteKey(projectId, currentUser);
        if (project == null) {
            throw new ResourceNotFoundException("Wiki not found for project: " + projectId);
        }
        if (!project.isHmWikiEnabled() || project.getHmWikiSlug() == null || project.getHmWikiSlug().isBlank()) {
            throw new ResourceNotFoundException("Wiki not found for project: " + projectId);
        }

        return project;
    }

    private JsonNode readJson(String body, String failureMessage) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new UpstreamServiceException(HttpStatus.BAD_GATEWAY, failureMessage, e);
        }
    }

    private String extractWikiId(JsonNode metadata, String projectId) {
        String id = textValue(metadata.path("mod").path("id"));
        if (id == null) {
            id = textValue(metadata.path("id"));
        }
        if (id == null) {
            throw new ResourceNotFoundException("Wiki not found for project: " + projectId);
        }
        return id;
    }

    private String resolvePagePath(String pagePath, JsonNode metadata) {
        String normalizedPagePath = normalizePagePath(pagePath);
        if (!normalizedPagePath.isBlank()) {
            return normalizedPagePath;
        }

        String indexSlug = textValue(metadata.path("index").path("slug"));
        if (indexSlug != null) {
            return indexSlug;
        }

        JsonNode pages = metadata.path("pages");
        if (pages.isArray() && pages.size() > 0) {
            String firstSlug = textValue(pages.get(0).path("slug"));
            if (firstSlug != null) {
                return firstSlug;
            }
        }

        throw new InvalidProjectRequestException("Invalid wiki page path.");
    }

    private String textValue(JsonNode node) {
        if (node == null || !node.isTextual()) return null;
        String value = node.asText().trim();
        return value.isBlank() ? null : value;
    }

    private String normalizePagePath(String pagePath) {
        if (pagePath == null) return "";
        String normalized = pagePath.trim();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        normalized = normalized.replace('\\', '/');
        if (normalized.isBlank()) return "";
        if (normalized.contains("..")
                || normalized.contains("//")
                || normalized.startsWith(".")) {
            throw new InvalidProjectRequestException("Invalid wiki page path.");
        }
        return normalized;
    }
}
