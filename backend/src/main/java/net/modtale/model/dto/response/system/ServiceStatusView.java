package net.modtale.model.dto.response.system;

import net.modtale.model.system.SystemStatus;

public record ServiceStatusView(
        String id,
        String name,
        SystemStatus status,
        int latency
) {
}
