package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.project.ProjectClassification;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectDependencyDTO(
        String projectId,
        String projectTitle,
        String versionNumber,
        String icon,
        String title,
        ProjectClassification classification,
        String slug,
        boolean isOptional,
        boolean isEmbedded
) {}
