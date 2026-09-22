package net.modtale.launcher.ui.library;

import java.util.List;
import java.util.Locale;

final class LibraryProjectFilter {
    private LibraryProjectFilter() {}

    static List<LibraryWorldProjectModel> matching(List<LibraryWorldProjectModel> projects, String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return projects;
        String[] terms = normalized.split("\\s+");
        return projects.stream().filter(project -> {
            var display = project.display();
            StringBuilder text = new StringBuilder(display.title()).append(' ').append(display.author())
                    .append(' ').append(display.version()).append(' ').append(display.metaNote());
            if (project.installed() != null) project.installed().files().forEach(file -> {
                int slash = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
                text.append(' ').append(file.substring(slash + 1));
            });
            project.contents().forEach(item -> text.append(' ').append(item.title()).append(' ').append(item.author()));
            String searchable = text.toString().toLowerCase(Locale.ROOT);
            for (String term : terms) if (!searchable.contains(term)) return false;
            return true;
        }).toList();
    }
}
