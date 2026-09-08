package net.modtale.launcher.model.project;

import java.util.List;

public record GameVersionCatalog(
        List<String> releaseVersions,
        List<String> preReleaseVersions,
        List<String> allVersions,
        List<GameVersionEntry> versions
) {
    public GameVersionCatalog {
        releaseVersions = releaseVersions == null ? List.of() : List.copyOf(releaseVersions);
        preReleaseVersions = preReleaseVersions == null ? List.of() : List.copyOf(preReleaseVersions);
        allVersions = allVersions == null ? List.of() : List.copyOf(allVersions);
        versions = versions == null ? List.of() : List.copyOf(versions);
    }

    public static GameVersionCatalog fromVersions(List<String> versions) {
        List<String> safeVersions = versions == null ? List.of() : versions;
        return new GameVersionCatalog(safeVersions, List.of(), safeVersions, List.of());
    }

    public record GameVersionEntry(
            String version,
            boolean preRelease,
            String sourceUrl
    ) {
    }
}
