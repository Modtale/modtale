package net.modtale.launcher.model.project;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectDetail(
        String id,
        String slug,
        String title,
        String about,
        String description,
        String authorId,
        String author,
        String imageUrl,
        String bannerUrl,
        String classification,
        int downloadCount,
        int favoriteCount,
        String updatedAt,
        String license,
        String repositoryUrl,
        Map<String, String> links,
        List<String> tags,
        List<String> galleryImages,
        Map<String, String> galleryImageCaptions,
        Boolean allowComments,
        boolean hmWikiEnabled,
        String hmWikiSlug,
        List<ProjectVersion> versions,
        List<ProjectRole> projectRoles,
        List<ProjectMember> teamMembers
) {
    public ProjectDetail {
        links = links == null ? Map.of() : Map.copyOf(links);
        tags = tags == null ? List.of() : List.copyOf(tags);
        galleryImages = galleryImages == null ? List.of() : List.copyOf(galleryImages);
        galleryImageCaptions = galleryImageCaptions == null ? Map.of() : Map.copyOf(galleryImageCaptions);
        versions = versions == null ? List.of() : List.copyOf(versions);
        projectRoles = projectRoles == null ? List.of() : List.copyOf(projectRoles);
        teamMembers = teamMembers == null ? List.of() : List.copyOf(teamMembers);
    }

    public ProjectDetail(
            String id, String slug, String title, String about, String description, String authorId, String author,
            String imageUrl, String bannerUrl, String classification, int downloadCount, int favoriteCount,
            String updatedAt, String license, String repositoryUrl, Map<String, String> links, List<String> tags,
            List<String> galleryImages, Map<String, String> galleryImageCaptions, Boolean allowComments,
            boolean hmWikiEnabled, String hmWikiSlug, List<ProjectVersion> versions
    ) {
        this(id, slug, title, about, description, authorId, author, imageUrl, bannerUrl, classification,
                downloadCount, favoriteCount, updatedAt, license, repositoryUrl, links, tags, galleryImages,
                galleryImageCaptions, allowComments, hmWikiEnabled, hmWikiSlug, versions, List.of(), List.of());
    }

    public ProjectDetail(
            String id,
            String slug,
            String title,
            String description,
            String author,
            String classification,
            String updatedAt,
            String license,
            String repositoryUrl,
            List<String> tags,
            List<ProjectVersion> versions
    ) {
        this(id, slug, title, null, description, null, author, null, null, classification, 0, 0, updatedAt,
                license, repositoryUrl, Map.of(), tags, List.of(), Map.of(), null, false, null, versions,
                List.of(), List.of());
    }

    public String routeKey() {
        return slug != null && !slug.isBlank() ? slug : id;
    }

    public ProjectDetail withVersions(List<ProjectVersion> nextVersions) {
        return new ProjectDetail(id, slug, title, about, description, authorId, author, imageUrl, bannerUrl,
                classification, downloadCount, favoriteCount, updatedAt, license, repositoryUrl, links, tags,
                galleryImages, galleryImageCaptions, allowComments, hmWikiEnabled, hmWikiSlug, nextVersions,
                projectRoles, teamMembers);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectRole(String id, String name, String color, Set<String> permissions) {
        public ProjectRole {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectMember(String userId, String roleId, String username, String avatarUrl) {
    }
}
