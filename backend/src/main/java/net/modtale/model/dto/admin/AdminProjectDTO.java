package net.modtale.model.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.dto.project.ProjectVersionSummaryDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
        ProjectStatus status,
        String expiresAt,
        LocalDateTime deletedAt,
        String approvedBy,
        List<String> contributors,
        List<String> galleryImages,
        List<Project.ProjectRole> projectRoles,
        List<Project.ProjectMember> teamMembers,
        List<Project.ProjectMember> teamInvites,
        List<ProjectVersionSummaryDTO> versions
) {}
