package net.modtale.launcher.model.worldlist;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorldModList(
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
        List<WorldModListItem> mods
) {
    public WorldModList {
        id = value(id);
        title = value(title);
        worldName = value(worldName);
        gameVersion = value(gameVersion);
        ownerUsername = value(ownerUsername);
        shareUrl = value(shareUrl);
        downloadUrl = value(downloadUrl);
        launcherInstallUrl = value(launcherInstallUrl);
        mods = mods == null ? List.of() : List.copyOf(mods);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
