package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectPageDTO(
        String id,
        String slug,
        String title,
        String about,
        String description,
        String authorId,
        String author,
        String imageUrl,
        String bannerUrl,
        ProjectClassification classification,
        List<String> tags,
        int downloadCount,
        int favoriteCount,
        String repositoryUrl,
        String updatedAt,
        String createdAt,
        String license,
        boolean customLicenseOpenSource,
        Map<String, String> links,
        boolean allowModpacks,
        boolean allowComments,
        boolean hmWikiEnabled,
        String hmWikiSlug,
        boolean galleryCarouselEnabled,
        ProjectStatus status,
        String expiresAt,
        boolean canEdit,
        boolean isOwner
) {
}
