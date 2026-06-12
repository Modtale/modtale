package net.modtale.model.dto.response.analytics;

import net.modtale.model.project.ProjectMeta;

import java.util.Map;
import java.util.stream.Collectors;

public record ProjectAnalyticsMetaView(
        String id,
        String title,
        long totalDownloads,
        String updatedAt
) {
    public static ProjectAnalyticsMetaView from(ProjectMeta meta) {
        if (meta == null) return null;
        return new ProjectAnalyticsMetaView(
                meta.getId(),
                meta.getTitle(),
                meta.getTotalDownloads(),
                meta.getUpdatedAt()
        );
    }

    static Map<String, ProjectAnalyticsMetaView> fromMap(Map<String, ProjectMeta> metas) {
        if (metas == null) return Map.of();
        return metas.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> from(entry.getValue())));
    }
}
