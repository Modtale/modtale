package net.modtale.model.dto.response.system;

import java.util.List;
import net.modtale.model.system.SystemStatus;

public record SystemStatusView(
        SystemStatus overall,
        List<ServiceStatusView> services,
        long timestamp,
        List<StatusHistoryPointView> history,
        List<StatusIncidentView> activeIncidents,
        List<StatusIncidentView> scheduledMaintenances,
        List<StatusIncidentView> incidentHistory
) {
}
