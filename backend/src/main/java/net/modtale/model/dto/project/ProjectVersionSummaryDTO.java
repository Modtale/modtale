package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.project.ProjectVersion;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectVersionSummaryDTO(
        String id,
        String versionNumber,
        List<String> gameVersions,
        int downloadCount,
        String releaseDate,
        ProjectVersion.Channel channel,
        ProjectVersion.ReviewStatus reviewStatus,
        String rejectionReason
) {}
