package net.modtale.model.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminProjectVersionSummaryDTO(
        String id,
        String versionNumber,
        List<String> gameVersions,
        int downloadCount,
        String releaseDate,
        ProjectVersion.Channel channel,
        ProjectVersion.ReviewStatus reviewStatus,
        String rejectionReason,
        ScanResult scanResult
) {}
