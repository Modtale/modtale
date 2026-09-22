package net.modtale.launcher.model.project;

import java.util.Comparator;
import java.util.List;

public record CurseForgeCommentsPage(List<ProjectComment> comments, int page, long totalCount, boolean hasMore) {
    public CurseForgeCommentsPage {
        comments = pinnedFirst(comments);
    }

    public static List<ProjectComment> pinnedFirst(List<ProjectComment> comments) {
        if (comments == null || comments.isEmpty()) return List.of();
        return comments.stream()
                .sorted(Comparator.comparing(ProjectComment::pinned).reversed())
                .toList();
    }
}
