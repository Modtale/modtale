package net.modtale.model.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import net.modtale.model.project.ScanStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminVerificationQueueScanDTO(
        ScanStatus status,
        String verdict,
        int riskScore,
        int knownIssueCount,
        int newIssueCount,
        int escalatedIssueCount
) {}
