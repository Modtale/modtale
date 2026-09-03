package net.modtale.launcher.ui.common;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GameVersionOrdering {
    private static final Pattern LEGACY_PATTERN = Pattern.compile("^(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})-([a-zA-Z0-9]+)$");
    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
                    + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
    );

    private GameVersionOrdering() {
    }

    public static List<String> descendingDistinct(List<String> versions) {
        if (versions == null || versions.isEmpty()) return List.of();
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        versions.stream().filter(version -> version != null && !version.isBlank())
                .map(String::trim).forEach(distinct::add);
        return distinct.stream().sorted(GameVersionOrdering::compare).toList();
    }

    public static int compare(String left, String right) {
        ParsedSemver semverLeft = parseSemver(left);
        ParsedSemver semverRight = parseSemver(right);
        ParsedLegacy legacyLeft = parseLegacy(left);
        ParsedLegacy legacyRight = parseLegacy(right);
        int leftRank = legacyLeft != null ? 1 : semverLeft != null ? 0 : 2;
        int rightRank = legacyRight != null ? 1 : semverRight != null ? 0 : 2;
        if (leftRank != rightRank) return Integer.compare(leftRank, rightRank);
        if (legacyLeft != null && legacyRight != null) {
            int date = legacyRight.dateKey().compareTo(legacyLeft.dateKey());
            return date != 0 ? date : legacyRight.hash().compareTo(legacyLeft.hash());
        }
        if (semverLeft != null && semverRight != null) return compareSemverDescending(semverLeft, semverRight);
        return right.compareTo(left);
    }

    private static ParsedLegacy parseLegacy(String version) {
        Matcher matcher = LEGACY_PATTERN.matcher(version == null ? "" : version);
        if (!matcher.matches()) return null;
        String dateKey = String.format(Locale.ROOT, "%04d%02d%02d",
                Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
        return new ParsedLegacy(dateKey, matcher.group(4));
    }

    private static ParsedSemver parseSemver(String version) {
        Matcher matcher = SEMVER_PATTERN.matcher(version == null ? "" : version);
        if (!matcher.matches()) return null;
        String prerelease = matcher.group(4);
        return new ParsedSemver(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                prerelease == null || prerelease.isBlank() ? List.of() : List.of(prerelease.split("\\.")));
    }

    private static int compareSemverDescending(ParsedSemver left, ParsedSemver right) {
        if (left.major() != right.major()) return Integer.compare(right.major(), left.major());
        if (left.minor() != right.minor()) return Integer.compare(right.minor(), left.minor());
        if (left.patch() != right.patch()) return Integer.compare(right.patch(), left.patch());
        boolean leftStable = left.prerelease().isEmpty();
        boolean rightStable = right.prerelease().isEmpty();
        if (leftStable != rightStable) return leftStable ? -1 : 1;
        if (leftStable) return 0;
        int length = Math.min(left.prerelease().size(), right.prerelease().size());
        for (int index = 0; index < length; index++) {
            String leftPart = left.prerelease().get(index);
            String rightPart = right.prerelease().get(index);
            boolean leftNumeric = leftPart.chars().allMatch(Character::isDigit);
            boolean rightNumeric = rightPart.chars().allMatch(Character::isDigit);
            if (leftNumeric && rightNumeric) {
                int comparison = Integer.compare(Integer.parseInt(rightPart), Integer.parseInt(leftPart));
                if (comparison != 0) return comparison;
            } else if (leftNumeric != rightNumeric) {
                return leftNumeric ? 1 : -1;
            } else {
                int comparison = rightPart.compareTo(leftPart);
                if (comparison != 0) return comparison;
            }
        }
        return Integer.compare(right.prerelease().size(), left.prerelease().size());
    }

    private record ParsedLegacy(String dateKey, String hash) {
    }

    private record ParsedSemver(int major, int minor, int patch, List<String> prerelease) {
    }
}
