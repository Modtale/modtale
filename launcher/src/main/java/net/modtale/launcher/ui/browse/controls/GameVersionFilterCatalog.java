package net.modtale.launcher.ui.browse.controls;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.modtale.launcher.model.project.GameVersionCatalog;

public final class GameVersionFilterCatalog {

    private static final GameVersionFilterCatalog EMPTY = new GameVersionFilterCatalog(List.of(), Set.of());

    private final List<String> allVersions;
    private final Set<String> preReleaseVersions;

    private GameVersionFilterCatalog(List<String> allVersions, Set<String> preReleaseVersions) {
        this.allVersions = allVersions;
        this.preReleaseVersions = preReleaseVersions;
    }

    public static GameVersionFilterCatalog empty() {
        return EMPTY;
    }

    public static GameVersionFilterCatalog fromVersions(List<String> versions) {
        return new GameVersionFilterCatalog(distinctVersions(versions), Set.of());
    }

    public static GameVersionFilterCatalog from(GameVersionCatalog catalog) {
        if (catalog == null) {
            return empty();
        }
        Set<String> preReleases = preReleaseVersions(catalog);
        List<String> ordered = orderedVersions(catalog);
        if (ordered.isEmpty()) {
            ordered = distinctVersions(catalog.releaseVersions());
        }
        return new GameVersionFilterCatalog(ordered, preReleases);
    }

    public boolean hasPreReleases() {
        return !preReleaseVersions.isEmpty();
    }

    public List<String> visibleVersions(boolean includePreReleases) {
        if (allVersions.isEmpty()) {
            return List.of();
        }
        if (!includePreReleases || preReleaseVersions.isEmpty()) {
            return allVersions.stream()
                    .filter(version -> !preReleaseVersions.contains(version))
                    .toList();
        }
        List<String> preReleases = allVersions.stream()
                .filter(preReleaseVersions::contains)
                .toList();
        List<String> releases = allVersions.stream()
                .filter(version -> !preReleaseVersions.contains(version))
                .toList();
        List<String> visible = new ArrayList<>(preReleases.size() + releases.size());
        visible.addAll(preReleases);
        visible.addAll(releases);
        return visible;
    }

    private static List<String> orderedVersions(GameVersionCatalog catalog) {
        if (catalog.versions() != null && !catalog.versions().isEmpty()) {
            return distinctVersions(catalog.versions().stream()
                    .filter(Objects::nonNull)
                    .map(GameVersionCatalog.GameVersionEntry::version)
                    .toList());
        }
        return distinctVersions(catalog.allVersions());
    }

    private static Set<String> preReleaseVersions(GameVersionCatalog catalog) {
        LinkedHashSet<String> versions = new LinkedHashSet<>();
        if (catalog.versions() != null && !catalog.versions().isEmpty()) {
            catalog.versions().stream()
                    .filter(Objects::nonNull)
                    .filter(GameVersionCatalog.GameVersionEntry::preRelease)
                    .map(GameVersionCatalog.GameVersionEntry::version)
                    .filter(version -> version != null && !version.isBlank())
                    .forEach(versions::add);
        }
        if (versions.isEmpty() && catalog.preReleaseVersions() != null) {
            catalog.preReleaseVersions().stream()
                    .filter(version -> version != null && !version.isBlank())
                    .forEach(versions::add);
        }
        return versions;
    }

    private static List<String> distinctVersions(List<String> versions) {
        if (versions == null || versions.isEmpty()) {
            return List.of();
        }
        return versions.stream()
                .filter(version -> version != null && !version.isBlank())
                .distinct()
                .toList();
    }
}
