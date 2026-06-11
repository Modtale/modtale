package net.modtale.service.project;

import net.modtale.config.properties.AppGameVersionProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GameVersionCatalogOrderingService {

    private static final Pattern GAME_VERSION_PATTERN = Pattern.compile("^(\\d{4})\\.(\\d{2})\\.(\\d{2})-([a-zA-Z0-9]+)$");
    private static final Pattern EXPLICIT_PRE_RELEASE_PATTERN = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)-pre\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");
    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
                    + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
    );

    private final AppGameVersionProperties gameVersionProperties;

    GameVersionCatalogOrderingService(AppGameVersionProperties gameVersionProperties) {
        this.gameVersionProperties = gameVersionProperties;
    }

    GameVersionService.GameVersionCatalog buildCatalog(GameVersionCatalogSourceService.GameVersionCatalogSource catalogSource) {
        return buildCatalog(
                catalogSource.releaseVersions(),
                catalogSource.preReleaseVersions(),
                catalogSource.indexedVersions()
        );
    }

    private GameVersionService.GameVersionCatalog buildCatalog(
            List<String> release,
            List<String> preRelease,
            List<String> indexed
    ) {
        Set<String> releaseSet = new HashSet<>(release);
        Set<String> preReleaseSet = new HashSet<>(preRelease);

        List<String> sortedRelease = sortDescDistinct(release);
        List<String> sortedPreRelease = sortDescDistinct(preRelease);
        List<String> sortedAll = sortDescDistinct(mergeLists(mergeLists(sortedRelease, sortedPreRelease), indexed));

        List<GameVersionService.GameVersionEntry> entries = new ArrayList<>(sortedAll.size());
        for (String version : sortedAll) {
            if (releaseSet.contains(version)) {
                entries.add(new GameVersionService.GameVersionEntry(version, false, gameVersionProperties.releaseUrl()));
            } else if (preReleaseSet.contains(version)) {
                entries.add(new GameVersionService.GameVersionEntry(version, true, gameVersionProperties.preReleaseUrl()));
            } else {
                entries.add(new GameVersionService.GameVersionEntry(version, isExplicitPreRelease(version), "indexed"));
            }
        }

        return new GameVersionService.GameVersionCatalog(sortedRelease, sortedPreRelease, sortedAll, entries);
    }

    private List<String> sortDescDistinct(List<String> input) {
        return input.stream().filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        set -> set.stream().sorted(this::compareGameVersions).toList()
                ));
    }

    private int compareGameVersions(String a, String b) {
        ParsedSemver semverA = parseSemver(a);
        ParsedSemver semverB = parseSemver(b);
        ParsedVersion legacyA = parseLegacyVersion(a);
        ParsedVersion legacyB = parseLegacyVersion(b);

        int rankA = semverA != null ? 0 : (legacyA != null ? 1 : 2);
        int rankB = semverB != null ? 0 : (legacyB != null ? 1 : 2);
        if (rankA != rankB) {
            return Integer.compare(rankA, rankB);
        }

        if (semverA != null && semverB != null) {
            return compareSemverDesc(semverA, semverB);
        }

        if (legacyA != null && legacyB != null) {
            int dateCompare = legacyB.dateKey.compareTo(legacyA.dateKey);
            if (dateCompare != 0) {
                return dateCompare;
            }
            return legacyB.hash.compareTo(legacyA.hash);
        }

        return b.compareTo(a);
    }

    private ParsedVersion parseLegacyVersion(String version) {
        Matcher matcher = GAME_VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            return null;
        }
        String dateKey = matcher.group(1) + matcher.group(2) + matcher.group(3);
        return new ParsedVersion(dateKey, matcher.group(4));
    }

    private ParsedSemver parseSemver(String version) {
        Matcher matcher = SEMVER_PATTERN.matcher(version);
        if (!matcher.matches()) {
            return null;
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        String preRelease = matcher.group(4);
        List<String> preReleaseIdentifiers = preRelease == null || preRelease.isBlank()
                ? List.of()
                : List.of(preRelease.split("\\."));

        return new ParsedSemver(major, minor, patch, preReleaseIdentifiers);
    }

    private int compareSemverDesc(ParsedSemver a, ParsedSemver b) {
        if (a.major != b.major) {
            return Integer.compare(b.major, a.major);
        }
        if (a.minor != b.minor) {
            return Integer.compare(b.minor, a.minor);
        }
        if (a.patch != b.patch) {
            return Integer.compare(b.patch, a.patch);
        }

        boolean aStable = a.preReleaseIdentifiers.isEmpty();
        boolean bStable = b.preReleaseIdentifiers.isEmpty();
        if (aStable && !bStable) {
            return -1;
        }
        if (!aStable && bStable) {
            return 1;
        }
        if (aStable) {
            return 0;
        }

        int minSize = Math.min(a.preReleaseIdentifiers.size(), b.preReleaseIdentifiers.size());
        for (int i = 0; i < minSize; i++) {
            String ai = a.preReleaseIdentifiers.get(i);
            String bi = b.preReleaseIdentifiers.get(i);
            boolean aNumeric = ai.chars().allMatch(Character::isDigit);
            boolean bNumeric = bi.chars().allMatch(Character::isDigit);

            if (aNumeric && bNumeric) {
                int an = Integer.parseInt(ai);
                int bn = Integer.parseInt(bi);
                if (an != bn) {
                    return Integer.compare(bn, an);
                }
            } else if (aNumeric != bNumeric) {
                return aNumeric ? 1 : -1;
            } else {
                int lex = bi.compareTo(ai);
                if (lex != 0) {
                    return lex;
                }
            }
        }

        return Integer.compare(b.preReleaseIdentifiers.size(), a.preReleaseIdentifiers.size());
    }

    private List<String> mergeLists(List<String> a, List<String> b) {
        List<String> merged = new ArrayList<>(a.size() + b.size());
        merged.addAll(a);
        merged.addAll(b);
        return merged;
    }

    private boolean isExplicitPreRelease(String version) {
        return version != null && EXPLICIT_PRE_RELEASE_PATTERN.matcher(version.trim()).matches();
    }

    private record ParsedVersion(String dateKey, String hash) {}
    private record ParsedSemver(int major, int minor, int patch, List<String> preReleaseIdentifiers) {}
}
