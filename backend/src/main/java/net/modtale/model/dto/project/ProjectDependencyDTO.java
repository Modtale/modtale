package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectDependencyDTO(
        String projectId,
        String projectTitle,
        String versionNumber,
        boolean isOptional,
        boolean isEmbedded
) {}
