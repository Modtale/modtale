package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.project.ProjectDependency;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectDependencyDTO(
        String id,
        String projectId,
        String projectTitle,
        String versionNumber,
        ProjectDependency.DependencyType dependencyType,
        ProjectDependency.Source source,
        String externalId,
        String externalUrl,
        String externalFileUrl,
        String externalFileName,
        String cachedFileUrl,
        boolean hytaleProjectConfirmed
) {}
