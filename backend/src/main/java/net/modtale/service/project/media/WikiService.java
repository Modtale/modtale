package net.modtale.service.project.media;

import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.exception.UpstreamServiceException;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.service.project.query.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class WikiService {

    private static final Logger logger = LoggerFactory.getLogger(WikiService.class);

    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final WikiUpstreamClient wikiUpstreamClient;

    public WikiService(ProjectService projectService, ObjectMapper objectMapper, WikiUpstreamClient wikiUpstreamClient) {
        this.projectService = projectService;
        this.objectMapper = objectMapper;
        this.wikiUpstreamClient = wikiUpstreamClient;
    }

    public JsonNode getWikiProject(String projectId, User currentUser) {
        String targetSlug = resolveProjectWikiSlug(projectId, currentUser);
        String wikiId = wikiUpstreamClient.resolveWikiModId(targetSlug);
        if (wikiId == null) {
            throw new ResourceNotFoundException("Wiki not found for project: " + projectId);
        }
        return readJson(wikiUpstreamClient.fetchProjectPayload(wikiId), "Failed to parse wiki project data.");
    }

    public JsonNode getWikiPage(String projectId, String pagePath, User currentUser) {
        String normalizedPagePath = normalizePagePath(pagePath);
        if (normalizedPagePath.isBlank()) {
            throw new InvalidProjectRequestException("Invalid wiki page path.");
        }

        String targetSlug = resolveProjectWikiSlug(projectId, currentUser);
        String wikiId = wikiUpstreamClient.resolveWikiModId(targetSlug);
        if (wikiId == null) {
            throw new ResourceNotFoundException("Wiki not found for project: " + projectId);
        }

        return readJson(wikiUpstreamClient.fetchPagePayload(wikiId, normalizedPagePath), "Failed to parse wiki page data.");
    }

    private String resolveProjectWikiSlug(String projectId, User currentUser) {
        Project project = projectService.getProjectByRouteKey(projectId, currentUser);
        if (project == null) {
            throw new ResourceNotFoundException("Wiki not found for project: " + projectId);
        }
        if (!project.isHmWikiEnabled() || project.getHmWikiSlug() == null || project.getHmWikiSlug().isBlank()) {
            throw new ResourceNotFoundException("Wiki not found for project: " + projectId);
        }

        return project.getHmWikiSlug().trim();
    }

    private JsonNode readJson(String body, String failureMessage) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException e) {
            logger.error("Wiki JSON parse failure: {}", e.getMessage(), e);
            throw new UpstreamServiceException(HttpStatus.BAD_GATEWAY, failureMessage, e);
        }
    }

    private String normalizePagePath(String pagePath) {
        if (pagePath == null) return "";
        String normalized = pagePath.trim();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        normalized = normalized.replace('\\', '/');
        if (normalized.isBlank()
                || normalized.contains("..")
                || normalized.contains("//")
                || normalized.startsWith(".")) {
            throw new InvalidProjectRequestException("Invalid wiki page path.");
        }
        return normalized;
    }
}
