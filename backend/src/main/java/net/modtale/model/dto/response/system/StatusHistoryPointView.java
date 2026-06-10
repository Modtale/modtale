package net.modtale.model.dto.response.system;

public record StatusHistoryPointView(
        long time,
        int api,
        int db,
        int storage
) {
}
