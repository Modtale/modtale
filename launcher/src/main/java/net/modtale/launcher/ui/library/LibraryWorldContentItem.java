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
        boolean toggleable,
        String source,
        List<String> hytaleCompatibility
) {
    LibraryWorldContentItem(String id, String title, String meta, String classification, String icon,
            String author, List<String> modIds, int enabledCount, int totalCount, boolean toggleable) {
        this(id, title, meta, classification, icon, author, modIds, enabledCount, totalCount, toggleable, "");
    }

    LibraryWorldContentItem(String id, String title, String meta, String classification, String icon,
            String author, List<String> modIds, int enabledCount, int totalCount, boolean toggleable, String source) {
        this(id, title, meta, classification, icon, author, modIds, enabledCount, totalCount, toggleable, source, List.of());
    }

    LibraryWorldContentItem {
        hytaleCompatibility = hytaleCompatibility == null ? List.of() : List.copyOf(hytaleCompatibility);
        id = value(id);
        title = value(title, "Installed content");
        meta = value(meta);
        classification = value(classification);
        icon = value(icon);
        author = value(author);
        source = value(source);
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
