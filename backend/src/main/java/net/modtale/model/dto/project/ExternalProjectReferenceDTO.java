package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
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
        Boolean distributionAllowed,
        List<ExternalFileDTO> files
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExternalFileDTO(
            String id,
            String displayName,
            String fileName,
            String versionNumber,
            String releaseType,
            String downloadUrl,
            Long fileSize,
            Map<String, String> hashes,
            List<String> gameVersions,
            Integer fileStatus,
            Boolean available
    ) {}
}
