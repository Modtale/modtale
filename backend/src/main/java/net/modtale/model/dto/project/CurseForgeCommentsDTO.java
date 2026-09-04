package net.modtale.model.dto.project;

import java.util.List;

public record CurseForgeCommentsDTO(List<Comment> comments, long totalCount) {
    public record Comment(
            String id,
            String user,
            Author author,
            String content,
            String date,
            boolean pinned,
            boolean readOnly,
            List<Comment> replies
    ) {}

    public record Author(String username, String avatarUrl) {}
}
