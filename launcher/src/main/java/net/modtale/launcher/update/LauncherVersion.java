package net.modtale.launcher.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LauncherVersion {

    public static final String VERSION_PROPERTY = "modtale.launcherVersion";
    private static final String FALLBACK_VERSION = "0.1.0-SNAPSHOT";

    private LauncherVersion() {
    }

    public static String current() {
        String configured = System.getProperty(VERSION_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }

        Package launcherPackage = LauncherVersion.class.getPackage();
        String implementationVersion = launcherPackage == null ? null : launcherPackage.getImplementationVersion();
        return implementationVersion == null || implementationVersion.isBlank()
                ? FALLBACK_VERSION
                : implementationVersion.trim();
    }

    public static boolean isNewer(String candidateVersion, String currentVersion) {
        return compare(candidateVersion, currentVersion) > 0;
    }

    static int compare(String left, String right) {
        VersionParts leftParts = VersionParts.parse(left);
        VersionParts rightParts = VersionParts.parse(right);
        int segmentCount = Math.max(leftParts.numbers().size(), rightParts.numbers().size());
        for (int i = 0; i < segmentCount; i++) {
            int leftNumber = i < leftParts.numbers().size() ? leftParts.numbers().get(i) : 0;
            int rightNumber = i < rightParts.numbers().size() ? rightParts.numbers().get(i) : 0;
            int comparison = Integer.compare(leftNumber, rightNumber);
            if (comparison != 0) {
                return comparison;
            }
        }

        if (leftParts.preRelease().isBlank() && rightParts.preRelease().isBlank()) {
            return 0;
        }
        if (leftParts.preRelease().isBlank()) {
            return 1;
        }
        if (rightParts.preRelease().isBlank()) {
            return -1;
        }
        return comparePreRelease(leftParts.preRelease(), rightParts.preRelease());
    }

    public static String normalizeTagVersion(String tagName) {
        if (tagName == null || tagName.isBlank()) {
            return "";
        }
        String version = tagName.trim();
        if (version.startsWith("refs/tags/")) {
            version = version.substring("refs/tags/".length());
        }
        if (version.startsWith("launcher-v")) {
            return version.substring("launcher-v".length());
        }
        if (version.startsWith("v")) {
            return version.substring(1);
        }
        return version;
    }

    private static int comparePreRelease(String left, String right) {
        String[] leftTokens = left.split("[.-]");
        String[] rightTokens = right.split("[.-]");
        int tokenCount = Math.max(leftTokens.length, rightTokens.length);
        for (int i = 0; i < tokenCount; i++) {
            if (i >= leftTokens.length) {
                return -1;
            }
            if (i >= rightTokens.length) {
                return 1;
            }
            String leftToken = leftTokens[i];
            String rightToken = rightTokens[i];
            boolean leftNumeric = leftToken.matches("\\d+");
            boolean rightNumeric = rightToken.matches("\\d+");
            int comparison;
            if (leftNumeric && rightNumeric) {
                comparison = Integer.compare(Integer.parseInt(leftToken), Integer.parseInt(rightToken));
            } else if (leftNumeric) {
                comparison = -1;
            } else if (rightNumeric) {
                comparison = 1;
            } else {
                comparison = leftToken.compareToIgnoreCase(rightToken);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private record VersionParts(List<Integer> numbers, String preRelease) {

        static VersionParts parse(String rawVersion) {
            String version = normalizeTagVersion(rawVersion).toLowerCase(Locale.ROOT);
            String[] pieces = version.split("-", 2);
            List<Integer> numbers = new ArrayList<>();
            for (String token : pieces[0].split("\\.")) {
                if (token.isBlank()) {
                    numbers.add(0);
                    continue;
                }
                String digits = token.replaceAll("[^0-9].*$", "");
                numbers.add(digits.isBlank() ? 0 : Integer.parseInt(digits));
            }
            String preRelease = pieces.length > 1 ? pieces[1] : "";
            return new VersionParts(numbers, preRelease);
        }
    }
}
