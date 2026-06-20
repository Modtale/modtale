package net.modtale.launcher.model.worldlist;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorldModListItem(
        String id,
        String modId,
        String projectId,
        String slug,
        String title,
        String versionNumber,
        String classification,
        String source,
        String externalId,
        String externalUrl,
        String icon,
        boolean downloadable,
        String unavailableReason
) {
    public WorldModListItem {
        id = value(id);
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
        unavailableReason = value(unavailableReason);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
