package net.modtale.model.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.user.Report;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportDTO(
        String id,
        String reporterId,
        String reporterUsername,
        String targetId,
        Report.TargetType targetType,
        String targetSummary,
        String reason,
        String description,
        Report.ReportStatus status,
        LocalDateTime createdAt,
        String resolvedBy,
        String resolutionNote
) {}
