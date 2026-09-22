package net.modtale.launcher.ui.library;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Display-only formatting: the original requirement remains the source of truth. */
final class ManifestVersionLabel {
    private static final Pattern VERSION = Pattern.compile("v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)");
    private static final Pattern TERM = Pattern.compile("(>=|<=|>|<|=)?\\s*(v?\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?)");
    private record V(int major, int minor, int patch) implements Comparable<V> {
        public int compareTo(V v) { int m = Integer.compare(major, v.major); if (m != 0) return m;
            m = Integer.compare(minor, v.minor); return m != 0 ? m : Integer.compare(patch, v.patch); }
        String series() { return major + "." + minor + ".x"; }
        public String toString() { return major + "." + minor + "." + patch; }
    }
    static String format(String raw, List<String> knownVersions) {
        String label = format(raw);
        var partial = Pattern.compile("(\\d+\\.\\d+\\.\\d+) – (\\d+)\\.(\\d+)\\.x").matcher(label);
        if (partial.matches()) {
            V start = parse(partial.group(1));
            V latest = knownVersions.stream().map(ManifestVersionLabel::parse).filter(v -> v != null
                    && v.major == start.major && v.minor == start.minor && v.compareTo(start) >= 0).max(V::compareTo).orElse(null);
            if (latest != null) return start.equals(latest) ? start.toString() : start + " – " + latest;
        }
        var preview = Pattern.compile(">=\\s*(\\d+\\.\\d+\\.\\d+-[^ ]+)\\s+<\\s*(\\d+\\.\\d+\\.\\d+)").matcher(raw == null ? "" : raw.trim());
        if (preview.matches()) {
            V upper = parse(preview.group(2));
            V lower = parse(preview.group(1).split("-", 2)[0]);
            if (upper != null && lower != null && lower.patch == 0 && upper.patch == 0
                    && upper.major == lower.major && upper.minor == lower.minor + 1) return lower.series();
        }
        return label;
    }
    static String format(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String value = normalizePartialBounds(raw.trim().replaceAll("\\s+", " "));
        if (value.startsWith("||") || value.endsWith("||")) return value;
        if (value.contains("||")) return String.join(" & ", Arrays.stream(value.split("\\s*\\|\\|\\s*", -1))
                .map(ManifestVersionLabel::format).distinct().toList());
        if (value.equals("*") || value.equalsIgnoreCase("x")) return "All versions";
        if (value.matches("v?\\d+\\.\\d+(?:\\.[xX*])?")) return value.replaceFirst("^v", "").replaceAll("\\.[xX*]$", "") + ".x";
        if (value.matches("v?\\d+(?:\\.[xX*])?(?:\\.[xX*])?")) return value.replaceFirst("^v", "").split("\\.")[0] + ".x";
        // Timestamp/hash builds are identifiers, not semantic-version ranges.
        if (value.matches("\\d{4}\\.\\d{2}\\.\\d{2}[-+].+")) return value;
        V exact = parse(value);
        if (exact != null) return exact.toString();
        if (value.matches("v?\\d+\\.\\d+\\.\\d+[-+].+")) return value.replaceFirst("^v", "");
        if (value.startsWith("^") || value.startsWith("~")) {
            String token = value.substring(1).trim();
            String[] pieces = token.replaceFirst("^v", "").split("\\.");
            V low = parse(token + (pieces.length == 1 ? ".0.0" : pieces.length == 2 ? ".0" : ""));
            if (low != null) {
                V upper = value.startsWith("~") ? (pieces.length == 1 ? new V(low.major + 1,0,0) : new V(low.major,low.minor + 1,0))
                        : low.major > 0 || pieces.length == 1 ? new V(low.major + 1,0,0)
                        : low.minor > 0 || pieces.length < 3 ? new V(0,low.minor + 1,0) : new V(0,0,low.patch + 1);
                return range(low, upper, false);
            }
        }
        String[] hyphen = value.split("\\s+[-–]\\s+", -1);
        if (hyphen.length == 2) {
            V lo = parse(hyphen[0]), hi = parse(hyphen[1]);
            if (lo != null && hi != null && lo.compareTo(hi) <= 0) return range(lo, hi, true);
        }
        var matcher = TERM.matcher(value);
        List<String[]> terms = new ArrayList<>(); int end = 0;
        while (matcher.find()) {
            if (!value.substring(end, matcher.start()).matches("[ ,&]*")) return value;
            terms.add(new String[]{matcher.group(1) == null ? "=" : matcher.group(1), matcher.group(2).replaceFirst("^v", "")});
            end = matcher.end();
        }
        if (end != value.length() || terms.isEmpty()) return value;
        if (terms.size() == 1) return words(terms.getFirst());
        if (terms.size() == 2) {
            String[] lower = terms.stream().filter(t -> t[0].equals(">=")).findFirst().orElse(null);
            String[] upper = terms.stream().filter(t -> t[0].equals("<") || t[0].equals("<=")).findFirst().orElse(null);
            if (lower != null && upper != null && (parse(lower[1]) == null || parse(upper[1]) == null)) {
                return lower[1] + " – " + (upper[0].equals("<") ? "before " : "") + upper[1];
            }
        }
        V lo = null, hi = null; boolean inclusive = false;
        for (String[] term : terms) {
            V v = parse(term[1]);
            if (v == null || term[0].equals("=")) return String.join(" & ", terms.stream().map(ManifestVersionLabel::words).toList());
            if (term[0].startsWith(">")) {
                if (term[0].equals(">")) return String.join(" & ", terms.stream().map(ManifestVersionLabel::words).toList());
                if (lo == null || lo.compareTo(v) < 0) lo = v;
            } else if (hi == null || hi.compareTo(v) > 0 || (hi.equals(v) && term[0].equals("<"))) {
                hi = v; inclusive = term[0].equals("<=");
            }
        }
        return lo != null && hi != null && lo.compareTo(hi) <= 0 ? range(lo,hi,inclusive)
                : String.join(" & ", terms.stream().map(ManifestVersionLabel::words).toList());
    }
    private static String normalizePartialBounds(String value) {
        var partial = Pattern.compile("(>=|<=|>|<)\\s*v?(\\d+)(?:\\.(\\d+))?(?:\\.[xX*])?(?=$|[ ,&|])").matcher(value);
        StringBuffer result = new StringBuffer();
        while (partial.find()) {
            try {
                int major = Integer.parseInt(partial.group(2));
                int minor = partial.group(3) == null ? 0 : Integer.parseInt(partial.group(3));
                String op = partial.group(1);
                if (op.equals(">") || op.equals("<=")) {
                    if (partial.group(3) == null) major = Math.addExact(major, 1);
                    else minor = Math.addExact(minor, 1);
                    op = op.equals(">") ? ">=" : "<";
                }
                partial.appendReplacement(result, op + major + "." + minor + ".0");
            } catch (ArithmeticException | NumberFormatException ignored) {
                partial.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(partial.group()));
            }
        }
        partial.appendTail(result);
        return result.toString();
    }
    private static String words(String[] term) {
        return switch(term[0]) {
            case ">=" -> term[1] + " and newer";
            case ">" -> "After " + term[1];
            case "<=" -> term[1] + " or older";
            case "<" -> "Before " + term[1];
            default -> term[1];
        };
    }
    private static String range(V lo, V hi, boolean inclusive) {
        if (!inclusive && lo.major == hi.major && hi.patch == 0 && hi.minor > lo.minor && lo.patch == 0) {
            if (hi.minor - lo.minor > 8) return lo.series() + " – " + new V(hi.major,hi.minor-1,0).series();
            List<String> series = new ArrayList<>();
            for (int minor=lo.minor; minor<hi.minor; minor++) series.add(new V(lo.major,minor,0).series());
            return String.join(" & ", series);
        }
        if (!inclusive && lo.minor == 0 && lo.patch == 0 && hi.major == lo.major + 1 && hi.minor == 0 && hi.patch == 0) return lo.major + ".x";
        if (!inclusive && lo.major == hi.major && hi.minor == lo.minor + 1 && hi.patch == 0) return lo + " – " + lo.series();
        if (!inclusive && lo.major == hi.major && lo.minor == hi.minor && hi.patch > lo.patch) hi = new V(hi.major,hi.minor,hi.patch-1);
        else if (!inclusive) return lo + " – before " + hi;
        return lo.equals(hi) ? lo.toString() : lo + " – " + hi;
    }
    private static V parse(String value) {
        var m = VERSION.matcher(value);
        if (!m.matches()) return null;
        try { return new V(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))); }
        catch (NumberFormatException ignored) { return null; }
    }
}
