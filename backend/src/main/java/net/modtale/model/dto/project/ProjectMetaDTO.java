package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.project.ProjectClassification;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectMetaDTO(
        String title,
        String description,
        String icon,
        String author,
        ProjectClassification classification,
        int downloads,
        String repositoryUrl,
        String slug
) {}
