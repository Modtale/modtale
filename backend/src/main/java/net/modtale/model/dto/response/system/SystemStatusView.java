package net.modtale.model.dto.response.system;

import java.util.List;

public record SystemStatusView(
        String overall,
        List<ServiceStatusView> services,
        long timestamp,
        List<StatusHistoryPointView> history
) {
}
