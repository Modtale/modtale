package net.modtale.model.dto.response.system;

import java.time.LocalDateTime;
import net.modtale.model.system.StatusIncidentState;
import net.modtale.model.system.SystemStatus;

public record StatusIncidentUpdateView(
        String id,
        StatusIncidentState state,
        SystemStatus impact,
        String message,
        LocalDateTime createdAt,
        String createdByUsername
) {
}
