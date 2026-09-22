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
        List<WorldModListItem> mods,
        List<WorldListConfig> configs
) {
    public WorldModList(String id, String title, String worldName, String gameVersion, String ownerUsername,
            Instant createdAt, Instant lastViewedAt, Instant expiresAt, int viewCount, int downloadCount,
            int modCount, int downloadableCount, String shareUrl, String downloadUrl, String launcherInstallUrl,
            List<WorldModListItem> mods) {
        this(id, title, worldName, gameVersion, ownerUsername, createdAt, lastViewedAt, expiresAt, viewCount,
                downloadCount, modCount, downloadableCount, shareUrl, downloadUrl, launcherInstallUrl, mods, List.of());
    }

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
        configs = configs == null ? List.of() : List.copyOf(configs);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
