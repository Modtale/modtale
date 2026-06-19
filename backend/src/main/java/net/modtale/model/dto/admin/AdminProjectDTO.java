package net.modtale.model.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminProjectDTO(
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
        Map<String, String> links,
        List<String> childProjectIds,
        boolean allowModpacks,
        boolean allowComments,
        boolean hmWikiEnabled,
        String hmWikiSlug,
        boolean galleryCarouselEnabled,
        ProjectStatus status,
        String expiresAt,
        LocalDateTime deletedAt,
        String approvedBy,
        List<String> galleryImages,
        Map<String, String> galleryImageCaptions,
        List<Project.ProjectRole> projectRoles,
        List<Project.ProjectMember> teamMembers,
        List<Project.ProjectMember> teamInvites,
        List<AdminProjectVersionSummaryDTO> versions
) {}
