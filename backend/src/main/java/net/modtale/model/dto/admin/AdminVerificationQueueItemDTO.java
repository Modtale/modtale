package net.modtale.model.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminVerificationQueueItemDTO(
        String id,
        String title,
        String description,
        String author,
        String imageUrl,
        ProjectClassification classification,
        ProjectStatus status,
        String updatedAt,
        AdminVerificationQueueVersionDTO pendingVersion
) {}
