package net.modtale.model.dto.response.system;

import java.time.LocalDateTime;
import java.util.List;
import net.modtale.model.system.StatusIncidentKind;
import net.modtale.model.system.StatusIncidentState;
import net.modtale.model.system.SystemStatus;

public record StatusIncidentView(
        String id,
        StatusIncidentKind kind,
        StatusIncidentState state,
        SystemStatus impact,
        String title,
        List<String> affectedServices,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        LocalDateTime startedAt,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String createdByUsername,
        List<StatusIncidentUpdateView> updates
) {
}
