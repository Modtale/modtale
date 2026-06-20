package net.modtale.launcher.model.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectSummary(
        String id,
        String slug,
        String title,
        String description,
        String authorId,
        String author,
        String imageUrl,
        String bannerUrl,
        String classification,
        int downloadCount,
        int favoriteCount,
        String updatedAt,
        List<ProjectVersion> versions
) {
    public ProjectSummary {
        versions = versions == null ? List.of() : List.copyOf(versions);
    }

    public String routeKey() {
        return slug != null && !slug.isBlank() ? slug : id;
    }

    public ProjectSummary withFavoriteCount(int nextFavoriteCount) {
        return new ProjectSummary(id, slug, title, description, authorId, author, imageUrl, bannerUrl, classification,
                downloadCount, Math.max(0, nextFavoriteCount), updatedAt, versions);
    }

    @Override
    public String toString() {
        return title == null || title.isBlank() ? routeKey() : title;
    }
}
