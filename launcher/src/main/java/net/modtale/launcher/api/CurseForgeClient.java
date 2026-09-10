package net.modtale.launcher.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.modtale.launcher.model.project.*;

/** Server-ordered discovery through the public CurseForge Core mirror. */
final class CurseForgeClient {
    private static final Map<String, Integer> CLASSES = Map.of(
            "mods", 9137, "prefabs", 9185, "worlds", 9184, "bootstrap", 9281, "translations", 10350);
    private final ModtaleApiTransport transport;
    private final URI base;

    CurseForgeClient(HttpClient client, ApiResponseCache cache) {
        this(client, cache, URI.create("https://api.curse.tools/v1/"));
    }

    CurseForgeClient(HttpClient client, ApiResponseCache cache, URI base) {
        transport = new ModtaleApiTransport(client, cache);
        this.base = base;
    }

    ProjectPage search(ProjectSearchQuery query) {
        int size = Math.max(1, query.size());
        long offset = (long) query.page() * size;
        if (offset >= 10000) return new ProjectPage(List.of(), (int) Math.ceil(10000.0 / size), 10000, query.page(), true);
        String filters = "&sortField=" + sortField(query.sort()) + "&sortOrder="
                + (List.of("name", "author").contains(query.sort()) ? "asc" : "desc");
        if (query.search() != null && !query.search().isBlank()) filters += "&searchFilter=" + encode(query.search());
        if (query.gameVersion() != null && !query.gameVersion().isBlank()) filters += "&gameVersion=" + encode(query.gameVersion());
        Integer classId = CLASSES.get(query.classification() == null ? "" : query.classification());
        if (classId != null) filters += "&classId=" + classId;
        List<JsonNode> items = new ArrayList<>();
        long total = 10000;
        long nextOffset = offset;
        while (nextOffset < Math.min(offset + size, total)) {
            int batchSize = (int) Math.min(50, Math.min(offset + size, total) - nextOffset);
            String path = "mods/search?gameId=70216&pageSize=" + batchSize + "&index=" + nextOffset + filters;
            JsonNode response = transport.get(base.resolve(path), JsonNode.class, Duration.ofMinutes(5));
            JsonNode batch = response.path("data");
            total = Math.min(10000, response.path("pagination").path("totalCount").asLong(nextOffset + batch.size()));
            batch.forEach(items::add);
            nextOffset += batch.size();
            if (batch.size() < batchSize) break;
        }
        List<ProjectSummary> projects = new ArrayList<>();
        for (JsonNode item : items) {
            if (item.path("gameId").asLong() != 70216 || item.path("id").asLong() <= 0) continue;
            String id = "curseforge:" + item.path("id").asLong();
            String website = item.path("links").path("websiteUrl").asText("");
            List<ProjectVersion> versions = versions(item.path("latestFiles"), website);
            List<String> authors = new ArrayList<>();
            item.path("authors").forEach(author -> authors.add(author.path("name").asText()));
            projects.add(new ProjectSummary(id, id, item.path("name").asText(), item.path("summary").asText(), null,
                    String.join(", ", authors), item.path("logo").path("thumbnailUrl").asText(null),
                    item.path("screenshots").path(0).path("thumbnailUrl").asText(null), "MOD",
                    bounded(item.path("downloadCount").asLong()), 0, item.path("dateModified").asText(null),
                    List.copyOf(versions), "CURSEFORGE", website,
                    !item.hasNonNull("allowModDistribution") || item.path("allowModDistribution").asBoolean()));
        }
        int pages = (int) Math.ceil(total / (double) size);
        return new ProjectPage(List.copyOf(projects), pages, total, query.page(), query.page() + 1 >= pages);
    }

    ProjectMeta projectMeta(long projectId) {
        JsonNode p = metadata(projectId);
        return new ProjectMeta(p.path("name").asText(), p.path("summary").asText(),
                media(p.path("logo").path("thumbnailUrl").asText()), String.join(", ", strings(p.path("authors"), "name")),
                "MOD", bounded(p.path("downloadCount").asLong()), null, "curseforge:" + projectId);
    }

    ProjectSummary enrichBrowseBanner(ProjectSummary summary) {
        if (summary == null || !summary.isCurseForge() || (summary.bannerUrl() != null && !summary.bannerUrl().isBlank())) return summary;
        try {
            String banner = media(metadata(summary.curseForgeProjectId()).path("screenshots").path(0).path("thumbnailUrl").asText());
            if (banner == null) return summary;
            return new ProjectSummary(summary.id(), summary.slug(), summary.title(), summary.description(), summary.authorId(),
                    summary.author(), summary.imageUrl(), banner, summary.classification(), summary.downloadCount(),
                    summary.favoriteCount(), summary.updatedAt(), summary.versions(), summary.source(), summary.websiteUrl(), summary.distributionAllowed());
        } catch (ModtaleApiException ignored) { return summary; }
    }

    ProjectDetail project(long projectId) {
        JsonNode p = metadata(projectId);
        String website = p.path("links").path("websiteUrl").asText();
        String about = null;
        try { about = get("mods/" + projectId + "/description", Duration.ofHours(1)).asText(null); }
        catch (ModtaleApiException ignored) { }
        List<ProjectVersion> files = new ArrayList<>();
        for (int offset = 0; offset < 10000; offset += 50) {
            JsonNode page = get("mods/" + projectId + "/files?pageSize=50&index=" + offset, Duration.ofMinutes(5));
            files.addAll(versions(page, website));
            if (page.size() < 50) break;
        }
        String id = "curseforge:" + projectId;
        return new ProjectDetail(id, id, p.path("name").asText(), about, p.path("summary").asText(), null,
                String.join(", ", strings(p.path("authors"), "name")), media(p.path("logo").path("thumbnailUrl").asText()),
                media(p.path("screenshots").path(0).path("url").asText()), "MOD", bounded(p.path("downloadCount").asLong()), 0,
                p.path("dateModified").asText(null), null, p.path("links").path("sourceUrl").asText(null),
                Map.of("CurseForge", website), strings(p.path("categories"), "name"),
                strings(p.path("screenshots"), "url").stream().filter(url -> media(url) != null).toList(), Map.of(),
                false, false, null, List.copyOf(files), List.of(), List.of());
    }

    DownloadUrlResponse download(long projectId, long fileId) {
        positive(projectId); positive(fileId);
        JsonNode file = get("mods/" + projectId + "/files/" + fileId, Duration.ZERO);
        if (file.path("id").asLong() != fileId || file.path("modId").asLong() != projectId
                || file.path("gameId").asLong() != 70216 || !file.path("isAvailable").asBoolean()) {
            throw new ModtaleApiException("This exact CurseForge file is unavailable.");
        }
        String url = media(file.path("downloadUrl").asText());
        if (url == null) url = "https://www.curseforge.com/api/v1/mods/" + projectId + "/files/" + fileId + "/download";
        Map<String, String> hashes = new java.util.LinkedHashMap<>();
        for (JsonNode hash : file.path("hashes")) {
            int algorithm = hash.path("algo").asInt();
            String value = hash.path("value").asText();
            if (algorithm == 1 && value.matches("(?i)[a-f0-9]{40}")) hashes.put("sha1", value.toLowerCase(java.util.Locale.ROOT));
            if (algorithm == 2 && value.matches("(?i)[a-f0-9]{32}")) hashes.put("md5", value.toLowerCase(java.util.Locale.ROOT));
        }
        return new DownloadUrlResponse(url, 0, file.path("fileName").asText(), file.path("fileLength").asLong(), hashes, "CURSEFORGE");
    }

    private JsonNode metadata(long id) {
        positive(id);
        JsonNode p = get("mods/" + id, Duration.ofMinutes(30));
        String website = p.path("links").path("websiteUrl").asText();
        if (p.path("id").asLong() != id || p.path("gameId").asLong() != 70216 || !p.path("isAvailable").asBoolean()
                || !website.matches("https://www\\.curseforge\\.com/hytale/(mods|prefabs|worlds|bootstrap|translations)/[a-z0-9-]+/?")) {
            throw new ModtaleApiException("CurseForge project was not found.", 404, null);
        }
        return p;
    }

    private JsonNode get(String path, Duration ttl) { return transport.get(base.resolve(path), JsonNode.class, ttl).path("data"); }
    private static void positive(long id) { if (id <= 0) throw new IllegalArgumentException("CurseForge IDs must be positive."); }
    private static String media(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1 && host != null
                    && (host.equalsIgnoreCase("forgecdn.net") || host.toLowerCase(java.util.Locale.ROOT).endsWith(".forgecdn.net")) ? value : null;
        } catch (IllegalArgumentException ignored) { return null; }
    }
    private static List<String> strings(JsonNode array, String field) {
        List<String> result = new ArrayList<>();
        for (JsonNode item : array) { String value = (field == null ? item : item.path(field)).asText(""); if (!value.isBlank()) result.add(value); }
        return List.copyOf(result);
    }
    private static List<ProjectVersion> versions(JsonNode files, String website) {
        List<ProjectVersion> result = new ArrayList<>();
        for (JsonNode file : files) {
            String id = file.path("id").asText();
            result.add(new ProjectVersion(id, file.path("displayName").asText(), strings(file.path("gameVersions"), null),
                    website + "/files/" + id, bounded(file.path("downloadCount").asLong()), file.path("fileDate").asText(),
                    null, List.of(), switch (file.path("releaseType").asInt()) { case 2 -> "BETA"; case 3 -> "ALPHA"; default -> "RELEASE"; }, List.of()));
        }
        return List.copyOf(result);
    }

    static int sortField(String sort) {
        return switch (sort) {
            case "popular" -> 2;
            case "updated" -> 3;
            case "name" -> 4;
            case "author" -> 5;
            case "downloads" -> 6;
            case "newest" -> 11;
            default -> 1;
        };
    }
    private static int bounded(long value) { return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value)); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
