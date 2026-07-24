package net.modtale.service.worldlist;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.model.dto.worldlist.WorldModListDTO;
import net.modtale.model.worldlist.WorldModList;
import org.springframework.stereotype.Component;

@Component
final class WorldModListMapper {

    private final String frontendUrl;

    WorldModListMapper(AppFrontendProperties frontendProperties) {
        this.frontendUrl = trimTrailingSlash(frontendProperties.url());
    }

    WorldModListDTO toDTO(WorldModList list) {
        if (list == null) {
            return null;
        }
        int downloadable = (int) list.getMods().stream()
                .filter(WorldModList.Item::isDownloadable)
                .count();
        String shareUrl = frontendUrl + "/lists/" + list.getId();
        String downloadUrl = "/lists/" + list.getId() + "/download";
        String launcherInstallUrl = "modtale://install-list?listId=" + encode(list.getId()) + "&url=" + encode(shareUrl);
        return new WorldModListDTO(
                list.getId(),
                list.getTitle(),
                list.getWorldName(),
                list.getGameVersion(),
                list.getOwnerUsername(),
                list.getCreatedAt(),
                list.getLastViewedAt(),
                list.getExpiresAt(),
                list.getViewCount(),
                list.getDownloadCount(),
                list.getMods().size(),
                downloadable,
                shareUrl,
                downloadUrl,
                launcherInstallUrl,
                list.getMods().stream().map(this::toItemDTO).toList()
        );
    }

    private WorldModListDTO.Item toItemDTO(WorldModList.Item item) {
        return new WorldModListDTO.Item(
                item.getId(),
                item.getModId(),
                item.getProjectId(),
                item.getSlug(),
                item.getTitle(),
                item.getAuthorId(),
                item.getAuthor(),
                item.getDescription(),
                item.getVersionNumber(),
                item.getClassification(),
                item.getSource(),
                item.getExternalId(),
                item.getExternalUrl(),
                item.getIcon(),
                item.getBannerUrl(),
                item.getDownloadCount(),
                item.getFavoriteCount(),
                item.getUpdatedAt(),
                item.isDownloadable(),
                item.getUnavailableReason()
        );
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value == null || value.isBlank() ? "https://modtale.net" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
