package net.modtale.launcher.ui.library;

import java.util.List;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.UpdateCandidate;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectMeta;

record LibraryWorldProjectModel(
        InstalledProject installed,
        ProjectDetail detail,
        ProjectMeta meta,
        UpdateCandidate update,
        boolean loading,
        List<String> modIds,
        int enabledCount,
        int totalCount,
        List<LibraryWorldContentItem> contents,
        LibraryWorldProjectDisplay display,
        boolean contentsCollapsed
) {
    LibraryWorldProjectModel(
            InstalledProject installed,
            ProjectDetail detail,
            ProjectMeta meta,
            UpdateCandidate update,
            boolean loading,
            List<String> modIds,
            int enabledCount,
            int totalCount,
            List<LibraryWorldContentItem> contents
    ) {
        this(installed, detail, meta, update, loading, modIds, enabledCount, totalCount, contents, null, false);
    }

    LibraryWorldProjectModel(
            InstalledProject installed,
            ProjectDetail detail,
            ProjectMeta meta,
            UpdateCandidate update,
            boolean loading,
            List<String> modIds,
            int enabledCount,
            int totalCount,
            List<LibraryWorldContentItem> contents,
            LibraryWorldProjectDisplay display
    ) {
        this(installed, detail, meta, update, loading, modIds, enabledCount, totalCount, contents, display, false);
    }

    LibraryWorldProjectModel {
        modIds = modIds == null
                ? List.of()
                : modIds.stream()
                .filter(modId -> modId != null && !modId.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        totalCount = Math.max(totalCount, modIds.size());
        enabledCount = Math.max(0, Math.min(enabledCount, totalCount));
        contents = contents == null ? List.of() : List.copyOf(contents);
        display = display == null ? LibraryWorldProjectDisplay.root(installed, meta) : display;
        contentsCollapsed = contentsCollapsed && display.contentsVisible();
    }

    boolean toggleable() {
        return !modIds.isEmpty();
    }

    boolean selected() {
        return toggleable() && totalCount > 0 && enabledCount == totalCount;
    }

    boolean indeterminate() {
        return toggleable() && enabledCount > 0 && enabledCount < totalCount;
    }

    boolean contentsExpanded() {
        return display.contentsVisible() && !contentsCollapsed;
    }
}
