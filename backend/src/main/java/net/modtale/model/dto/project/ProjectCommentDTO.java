package net.modtale.model.dto.project;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectCommentDTO(
        String id,
        String userId,
        String content,
        String date,
        String updatedAt,
        int upvoteCount,
        int downvoteCount,
        String userVote,
        ProjectCommentReplyDTO developerReply
) {}
