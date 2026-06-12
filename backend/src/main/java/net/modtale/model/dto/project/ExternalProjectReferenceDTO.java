package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import net.modtale.model.project.ProjectDependency;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExternalProjectReferenceDTO(
        ProjectDependency.Source source,
        String externalId,
        String title,
        String versionNumber,
        String externalUrl,
        String iconUrl,
        String summary,
        boolean hytaleProjectConfirmed,
        List<ExternalFileDTO> files
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExternalFileDTO(
            String id,
            String displayName,
            String fileName,
            String versionNumber,
            String releaseType,
            String downloadUrl
    ) {}
}
