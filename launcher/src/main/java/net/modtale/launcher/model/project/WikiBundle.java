package net.modtale.launcher.model.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WikiBundle(
        ProjectDetail project,
        JsonNode metadata,
        JsonNode page,
        String pageSlug
) {
    public List<WikiNode> pages() {
        JsonNode pages = metadata == null ? null : metadata.path("mod").path("pages");
        if (pages == null || !pages.isArray()) {
            pages = metadata == null ? null : metadata.path("pages");
        }
        if (pages == null || !pages.isArray()) {
            return List.of();
        }
        List<WikiNode> result = new ArrayList<>();
        pages.forEach(node -> result.add(WikiNode.from(node)));
        return List.copyOf(result);
    }

    public String indexSlug() {
        String nested = text(metadata == null ? null : metadata.path("mod").path("index").path("slug"));
        return nested != null ? nested : text(metadata == null ? null : metadata.path("index").path("slug"));
    }

    public String wikiName() {
        String nested = text(metadata == null ? null : metadata.path("mod").path("name"));
        return nested != null ? nested : text(metadata == null ? null : metadata.path("name"));
    }

    public WikiPage content() {
        return WikiPage.from(page);
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isTextual()) return null;
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    public record WikiNode(String id, String slug, String title, List<WikiNode> children) {
        private static WikiNode from(JsonNode node) {
            List<WikiNode> children = new ArrayList<>();
            JsonNode childNodes = node == null ? null : node.path("children");
            if (childNodes != null && childNodes.isArray()) {
                childNodes.forEach(child -> children.add(from(child)));
            }
            String slug = text(node == null ? null : node.path("slug"));
            String title = first(
                    text(node == null ? null : node.path("title")),
                    text(node == null ? null : node.path("name")),
                    slug,
                    "Untitled page"
            );
            return new WikiNode(
                    first(text(node == null ? null : node.path("id")), slug, title),
                    slug,
                    title,
                    List.copyOf(children)
            );
        }

        public boolean contains(String targetSlug) {
            if (targetSlug == null) return false;
            if (targetSlug.equals(slug)) return true;
            return children.stream().anyMatch(child -> child.contains(targetSlug));
        }
    }

    public record WikiPage(String title, String content) {
        private static WikiPage from(JsonNode node) {
            if (node == null || node.isNull() || node.isMissingNode()) {
                return new WikiPage(null, null);
            }
            return new WikiPage(text(node.path("title")), text(node.path("content")));
        }

        public boolean empty() {
            return content == null || content.isBlank();
        }
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
