package net.modtale.launcher.ui.library;

import java.util.List;

record LibraryWorldContentItem(
        String id,
        String title,
        String meta,
        String classification,
        String icon,
        String author,
        List<String> modIds,
        int enabledCount,
        int totalCount,
        boolean toggleable
) {
    LibraryWorldContentItem {
        id = value(id);
        title = value(title, "Installed content");
        meta = value(meta);
        classification = value(classification);
        icon = value(icon);
        author = value(author);
        modIds = modIds == null
                ? List.of()
                : modIds.stream()
                .filter(modId -> modId != null && !modId.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        totalCount = Math.max(totalCount, modIds.size());
        enabledCount = Math.max(0, Math.min(enabledCount, totalCount));
        toggleable = toggleable && !modIds.isEmpty();
    }

    boolean selected() {
        return toggleable && totalCount > 0 && enabledCount == totalCount;
    }

    boolean indeterminate() {
        return toggleable && enabledCount > 0 && enabledCount < totalCount;
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static String value(String value, String fallback) {
        String normalized = value(value);
        return normalized.isBlank() ? fallback : normalized;
    }
}
