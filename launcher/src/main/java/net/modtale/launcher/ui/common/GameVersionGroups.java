package net.modtale.launcher.ui.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GameVersionGroups {

    private GameVersionGroups() {
    }

    public static List<String> parseSelection(String value) {
        if (value == null || value.isBlank() || "Any".equalsIgnoreCase(value.trim())) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(version -> !version.isBlank() && !"Any".equalsIgnoreCase(version))
                .distinct()
                .toList();
    }

    public static String selectionQuery(List<String> selectedVersions, List<String> orderedVersions) {
        List<String> ordered = orderedSelection(selectedVersions, orderedVersions);
        return ordered.isEmpty() ? null : String.join(",", ordered);
    }

    public static List<String> orderedSelection(List<String> selectedVersions, List<String> orderedVersions) {
        if (selectedVersions == null || selectedVersions.isEmpty()) {
            return List.of();
        }
        Set<String> selected = new LinkedHashSet<>(selectedVersions);
        List<String> ordered = new ArrayList<>();
        if (orderedVersions != null) {
            for (String version : orderedVersions) {
                if (selected.remove(version)) {
                    ordered.add(version);
                }
            }
        }
        ordered.addAll(selected);
        return List.copyOf(ordered);
    }

    public static String rangeLabel(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        String base = version.split("-", 2)[0];
        String[] parts = base.split("\\.");
        if (parts.length < 2 || !isDigits(parts[0]) || !isDigits(parts[1])) {
            return null;
        }
        return parts[0] + "." + parts[1] + ".x";
    }

    public static List<Group> build(List<String> versions) {
        if (versions == null || versions.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String version : versions) {
            if (version == null || version.isBlank()) {
                continue;
            }
            String label = rangeLabel(version);
            String key = label == null ? version : label;
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(version);
        }
        return groups.entrySet().stream()
                .map(entry -> new Group(entry.getKey(), List.copyOf(entry.getValue()), entry.getValue().size() > 1))
                .toList();
    }

    public static String displayLabel(List<String> selectedVersions, List<String> orderedVersions, String anyLabel) {
        List<String> selected = orderedSelection(selectedVersions, orderedVersions);
        if (selected.isEmpty()) {
            return anyLabel == null || anyLabel.isBlank() ? "Any" : anyLabel;
        }
        for (Group group : build(orderedVersions)) {
            if (group.grouped()
                    && selected.size() == group.versions().size()
                    && selected.containsAll(group.versions())) {
                return group.label();
            }
        }
        if (selected.size() == 1) {
            return selected.getFirst();
        }
        return selected.size() + " versions";
    }

    private static boolean isDigits(String value) {
        return value != null && !value.isBlank() && value.chars().allMatch(Character::isDigit);
    }

    public record Group(String label, List<String> versions, boolean grouped) {
        public Group {
            versions = versions == null ? List.of() : List.copyOf(versions);
        }
    }
}
