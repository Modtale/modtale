package net.modtale.model.dto.response.project;

import net.modtale.service.project.GameVersionService;

import java.util.List;

public record GameVersionCatalogView(
        List<String> releaseVersions,
        List<String> preReleaseVersions,
        List<String> allVersions,
        List<GameVersionEntryView> versions
) {
    public static GameVersionCatalogView from(GameVersionService.GameVersionCatalog catalog) {
        if (catalog == null) return new GameVersionCatalogView(List.of(), List.of(), List.of(), List.of());
        return new GameVersionCatalogView(
                catalog.releaseVersions(),
                catalog.preReleaseVersions(),
                catalog.allVersions(),
                catalog.versions() == null ? List.of() : catalog.versions().stream().map(GameVersionEntryView::from).toList()
        );
    }

    public record GameVersionEntryView(
            String version,
            boolean preRelease,
            String sourceUrl
    ) {
        private static GameVersionEntryView from(GameVersionService.GameVersionEntry entry) {
            if (entry == null) return null;
            return new GameVersionEntryView(entry.version(), entry.preRelease(), entry.sourceUrl());
        }
    }
}
