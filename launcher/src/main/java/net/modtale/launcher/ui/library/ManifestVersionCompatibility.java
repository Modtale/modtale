package net.modtale.launcher.ui.library;

import java.util.regex.Pattern;
import net.modtale.launcher.ui.common.GameVersionOrdering;

final class ManifestVersionCompatibility {
    private static final String VERSION = "(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?";
    private static final Pattern TERM = Pattern.compile("(>=|<=|>|<|=)?\\s*(v?" + VERSION + ")");

    static boolean incompatible(String requirement, String current) {
        if (requirement == null || current == null || !current.matches(VERSION)
                || current.matches("\\d{4}\\.\\d{2}\\.\\d{2}[-+].*")) return false;
        try {
            for (String alternative : requirement.trim().split("\\|\\|", -1)) {
                Boolean matches = matches(alternative.trim(), current);
                // Unknown syntax is not evidence of incompatibility.
                if (matches == null || matches) return false;
            }
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Boolean matches(String range, String current) {
        if (range.equals("*") || range.equalsIgnoreCase("x")) return true;
        if (range.matches("v?\\d+(?:\\.\\d+)?(?:\\.[xX*])?")) {
            String prefix = range.replaceFirst("^v", "").replaceFirst("\\.[xX*]$", "");
            return current.startsWith(prefix + ".");
        }
        String[] hyphen = range.split("\\s+-\\s+", -1);
        if (hyphen.length == 2 && hyphen[0].matches(VERSION) && hyphen[1].matches(VERSION)) {
            range = ">=" + hyphen[0] + " <=" + hyphen[1];
        }
        var matcher = TERM.matcher(range);
        int end = 0;
        boolean result = true;
        boolean found = false;
        while (matcher.find()) {
            if (!range.substring(end, matcher.start()).matches("[ ,&]*")) return null;
            found = true;
            end = matcher.end();
            String bound = matcher.group(2).replaceFirst("^v", "");
            if (bound.matches("\\d{4}\\.\\d{2}\\.\\d{2}[-+].*")) return null;
            int comparison = GameVersionOrdering.compare(bound, current);
            result &= switch (matcher.group(1) == null ? "=" : matcher.group(1)) {
                case ">=" -> comparison >= 0;
                case ">" -> comparison > 0;
                case "<=" -> comparison <= 0;
                case "<" -> comparison < 0;
                default -> comparison == 0;
            };
        }
        return found && end == range.length() ? result : null;
    }
}
