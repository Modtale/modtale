package net.modtale.model.dto.response.system;

public record ServiceStatusView(
        String id,
        String name,
        String status,
        int latency
) {
}
