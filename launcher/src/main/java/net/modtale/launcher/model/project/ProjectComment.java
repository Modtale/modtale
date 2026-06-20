package net.modtale.launcher.model.project;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectComment(
        String id,
        @JsonAlias("authorId") String userId,
        String user,
        Author author,
        String content,
        String date,
        String updatedAt,
        Integer upvoteCount,
        Integer downvoteCount,
        String userVote,
        List<String> upvotes,
        List<String> downvotes,
        Reply developerReply
) {
    public ProjectComment {
        upvotes = upvotes == null ? List.of() : List.copyOf(upvotes);
        downvotes = downvotes == null ? List.of() : List.copyOf(downvotes);
    }

    public int score() {
        if (upvoteCount != null || downvoteCount != null) {
            return Math.max(0, upvoteCount == null ? 0 : upvoteCount)
                    - Math.max(0, downvoteCount == null ? 0 : downvoteCount);
        }
        return upvotes.size() - downvotes.size();
    }

    public String userVoteFor(String currentUserId) {
        if ("up".equalsIgnoreCase(userVote) || "down".equalsIgnoreCase(userVote)) {
            return userVote.toLowerCase(java.util.Locale.ROOT);
        }
        if (currentUserId != null && upvotes.contains(currentUserId)) {
            return "up";
        }
        if (currentUserId != null && downvotes.contains(currentUserId)) {
            return "down";
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reply(
            @JsonAlias("authorId") String userId,
            String user,
            Author author,
            String content,
            String date,
            Integer upvoteCount,
            Integer downvoteCount,
            String userVote,
            List<String> upvotes,
            List<String> downvotes
    ) {
        public Reply {
            upvotes = upvotes == null ? List.of() : List.copyOf(upvotes);
            downvotes = downvotes == null ? List.of() : List.copyOf(downvotes);
        }

        public int score() {
            if (upvoteCount != null || downvoteCount != null) {
                return Math.max(0, upvoteCount == null ? 0 : upvoteCount)
                        - Math.max(0, downvoteCount == null ? 0 : downvoteCount);
            }
            return upvotes.size() - downvotes.size();
        }

        public String userVoteFor(String currentUserId) {
            if ("up".equalsIgnoreCase(userVote) || "down".equalsIgnoreCase(userVote)) {
                return userVote.toLowerCase(java.util.Locale.ROOT);
            }
            if (currentUserId != null && upvotes.contains(currentUserId)) {
                return "up";
            }
            if (currentUserId != null && downvotes.contains(currentUserId)) {
                return "down";
            }
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Author(
            String id,
            String username,
            String avatarUrl
    ) {
    }
}
