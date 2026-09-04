package net.modtale.launcher.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.modtale.launcher.model.project.CurseForgeCommentsPage;
import net.modtale.launcher.model.project.ProjectComment;

/** Reads public comments directly from CurseForge without using the Modtale API. */
final class CurseForgeCommentsClient {

    private static final URI DEFAULT_BASE_URI = URI.create("https://www.curseforge.com");
    private static final int MAX_COMMENTS_PER_PAGE = 100;
    private static final int MAX_REPLY_DEPTH = 20;
    private static final int MAX_CONTENT_LENGTH = 20_000;

    private final HttpClient httpClient;
    private final URI baseUri;
    private final ObjectMapper mapper = new ObjectMapper();

    CurseForgeCommentsClient(HttpClient httpClient) {
        this(httpClient, DEFAULT_BASE_URI);
    }

    CurseForgeCommentsClient(HttpClient httpClient, URI baseUri) {
        this.httpClient = httpClient;
        this.baseUri = baseUri;
    }

    CurseForgeCommentsPage getComments(long projectId, int page) {
        if (projectId <= 0) throw new IllegalArgumentException("CurseForge project IDs must be positive.");
        if (page < 0) throw new IllegalArgumentException("CurseForge comment pages cannot be negative.");
        List<ProjectComment> comments = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        JsonNode envelope = get(projectId, page);
        JsonNode data = envelope.path("data");
        if (!data.isArray()) throw new ModtaleApiException("CurseForge returned an invalid comments response.");
        for (JsonNode node : data) {
            ProjectComment comment = parseComment(node, projectId, seen, 0);
            if (comment != null) comments.add(comment);
            if (seen.size() >= MAX_COMMENTS_PER_PAGE) break;
        }
        JsonNode pagination = envelope.path("pagination");
        long totalCount = Math.max(seen.size(), pagination.path("totalCount").asLong(seen.size()));
        int pageSize = Math.max(1, pagination.path("pageSize").asInt(Math.max(1, seen.size())));
        boolean hasMore = ((long) page + 1) * pageSize < totalCount && !data.isEmpty();
        return new CurseForgeCommentsPage(comments, page, totalCount, hasMore);
    }

    private JsonNode get(long projectId, int page) {
        URI uri = baseUri.resolve("/api/v1/mods/" + projectId + "/comments?page=" + page);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "ModtaleLauncher/0.1 (+https://modtale.net)")
                .GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ModtaleApiTransport.ensureSuccess(response.statusCode(), uri.toString());
            return mapper.readTree(response.body());
        } catch (IOException ex) {
            throw new ModtaleApiException("Could not read comments from CurseForge.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ModtaleApiException("The CurseForge comments request was interrupted.", ex);
        }
    }

    private ProjectComment parseComment(JsonNode node, long projectId, Set<Long> seen, int depth) {
        long id = node.path("id").asLong(0);
        if (depth > MAX_REPLY_DEPTH || seen.size() >= MAX_COMMENTS_PER_PAGE || id <= 0
                || node.path("projectId").asLong(projectId) != projectId || !seen.add(id)
                || (node.hasNonNull("status") && node.path("status").asInt() != 1)) {
            return null;
        }
        String content = text(node, "text");
        if (content == null) return null;
        if (content.length() > MAX_CONTENT_LENGTH) content = content.substring(0, MAX_CONTENT_LENGTH);

        JsonNode authorNode = node.path("author");
        String username = first(text(authorNode, "displayName"), text(authorNode, "username"), "CurseForge user");
        String avatarUrl = approvedAvatar(text(authorNode, "twitchAvatarUrl"));
        List<ProjectComment> replies = new ArrayList<>();
        JsonNode replyNodes = node.path("replies");
        if (replyNodes.isArray()) for (JsonNode replyNode : replyNodes) {
            ProjectComment reply = parseComment(replyNode, projectId, seen, depth + 1);
            if (reply != null) replies.add(reply);
        }

        return new ProjectComment("curseforge:" + id, null, username,
                new ProjectComment.Author(null, username, avatarUrl), content, date(node.path("datePosted")),
                null, node.path("isPinned").asBoolean(false), null, null, null, List.of(), List.of(), null,
                true, List.copyOf(replies));
    }

    private static String date(JsonNode value) {
        long epochMillis = value.asLong(0);
        if (epochMillis <= 0) return null;
        try {
            return Instant.ofEpochMilli(epochMillis).toString();
        } catch (DateTimeException ignored) {
            return null;
        }
    }

    private static String approvedAvatar(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value.replace("{0}", "70x70"));
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1
                    || host == null) return null;
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return normalizedHost.equals("static-cdn.jtvnw.net")
                    || normalizedHost.equals("forgecdn.net")
                    || normalizedHost.endsWith(".forgecdn.net") ? uri.toString() : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).textValue();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}
