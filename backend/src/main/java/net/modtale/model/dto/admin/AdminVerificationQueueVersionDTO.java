package net.modtale.model.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.project.ProjectVersion;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminVerificationQueueVersionDTO(
        String id,
        String versionNumber,
        String changelog,
        ProjectVersion.ReviewStatus reviewStatus,
        AdminVerificationQueueScanDTO scan
) {}
