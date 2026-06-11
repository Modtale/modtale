package net.modtale.model.dto.response.analytics;

public record PlatformStatsView(
        long totalProjects,
        long totalUsers,
        long totalDownloads
) {
}
