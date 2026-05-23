package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectCommentReplyDTO(
        String userId,
        String content,
        String date,
        int upvoteCount,
        int downvoteCount,
        String userVote
) {}
