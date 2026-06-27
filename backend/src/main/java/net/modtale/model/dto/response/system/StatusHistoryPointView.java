package net.modtale.model.dto.response.system;

import net.modtale.model.system.SystemStatus;

public record StatusHistoryPointView(
        long time,
        int api,
        int db,
        int storage,
        SystemStatus apiStatus,
        SystemStatus dbStatus,
        SystemStatus storageStatus
) {
}
