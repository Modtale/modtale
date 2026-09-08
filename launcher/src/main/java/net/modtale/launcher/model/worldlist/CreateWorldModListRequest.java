package net.modtale.launcher.model.worldlist;

import java.util.List;
import net.modtale.launcher.model.worldlist.WorldListConfig;

public record CreateWorldModListRequest(
        String title,
        String worldName,
        String gameVersion,
        List<Item> mods,
        List<WorldListConfig> configs
) {
    public CreateWorldModListRequest(String title, String worldName, String gameVersion, List<Item> mods) {
        this(title, worldName, gameVersion, mods, List.of());
    }
    public CreateWorldModListRequest {
        title = value(title);
        worldName = value(worldName);
        gameVersion = value(gameVersion);
        mods = mods == null ? List.of() : List.copyOf(mods);
    }

    public record Item(
            String modId,
            String projectId,
            String slug,
            String title,
            String versionNumber,
            String classification,
            String source,
            String externalId,
            String externalUrl,
            String icon
    ) {
        public Item {
            modId = value(modId);
            projectId = value(projectId);
            slug = value(slug);
            title = value(title);
            versionNumber = value(versionNumber);
            classification = value(classification);
            source = value(source);
            externalId = value(externalId);
            externalUrl = value(externalUrl);
            icon = value(icon);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
