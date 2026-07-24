package net.modtale.model.dto.worldlist;

import java.time.Instant;
import java.util.List;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectDependency;

public record WorldModListDTO(
        String id,
        String title,
        String worldName,
        String gameVersion,
        String ownerUsername,
        Instant createdAt,
        Instant lastViewedAt,
        Instant expiresAt,
        int viewCount,
        int downloadCount,
        int modCount,
        int downloadableCount,
        String shareUrl,
        String downloadUrl,
        String launcherInstallUrl,
        List<Item> mods
) {
    public record Item(
            String id,
            String modId,
            String projectId,
            String slug,
            String title,
            String authorId,
            String author,
            String description,
            String versionNumber,
            ProjectClassification classification,
            ProjectDependency.Source source,
            String externalId,
            String externalUrl,
            String icon,
            String bannerUrl,
            int downloadCount,
            int favoriteCount,
            String updatedAt,
            boolean downloadable,
            String unavailableReason
    ) {}
}
