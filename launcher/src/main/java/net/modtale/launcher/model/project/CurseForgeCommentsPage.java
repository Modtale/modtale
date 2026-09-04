package net.modtale.launcher.model.project;

import java.util.List;

public record CurseForgeCommentsPage(List<ProjectComment> comments, int page, long totalCount, boolean hasMore) {
    public CurseForgeCommentsPage {
        comments = comments == null ? List.of() : List.copyOf(comments);
    }
}
