package net.modtale.launcher.ui.library;

import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.project.ProjectMeta;

record LibraryWorldProjectDisplay(
        String title,
        String author,
        String classification,
        String icon,
        String version,
        String metaNote,
        boolean localFile,
        boolean unlockVisible,
        boolean contentsVisible
) {
    LibraryWorldProjectDisplay {
        title = value(title, "Untitled Project");
        author = value(author);
        classification = value(classification, "PLUGIN");
        icon = value(icon);
        version = value(version);
        metaNote = value(metaNote);
    }

    static LibraryWorldProjectDisplay root(InstalledProject installed, ProjectMeta meta) {
        return new LibraryWorldProjectDisplay(
                first(meta == null ? "" : meta.title(), installed == null ? "" : installed.title()),
                meta == null ? "" : meta.author(),
                first(meta == null ? "" : meta.classification(), installed == null ? "" : installed.classification()),
                meta == null ? "" : meta.icon(),
                installed == null ? "" : installed.installedVersion(),
                "",
                installed != null && !LibraryProjectSupport.isModtaleProject(installed),
                installed != null && installed.isModpack(),
                installed != null && installed.isModpack()
        );
    }

    private static String first(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static String value(String value, String fallback) {
        String normalized = value(value);
        return normalized.isBlank() ? fallback : normalized;
    }
}
