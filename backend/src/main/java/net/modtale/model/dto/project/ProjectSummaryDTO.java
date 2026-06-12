package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectSummaryDTO(
        String id,
        String slug,
        String title,
        String description,
        String authorId,
        String author,
        String imageUrl,
        String bannerUrl,
        ProjectClassification classification,
        int downloadCount,
        int favoriteCount,
        String updatedAt,
        List<String> childProjectIds,
        ProjectStatus status,
        Boolean canEdit,
        Boolean isOwner,
        List<ProjectVersionSummaryDTO> versions
) {}
