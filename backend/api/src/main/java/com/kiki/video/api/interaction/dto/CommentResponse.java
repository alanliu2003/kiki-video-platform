package com.kiki.video.api.interaction.dto;

import com.kiki.video.api.interaction.model.Comment;

import java.time.Instant;
import java.util.List;

public record CommentResponse(
        Long id,
        Long videoId,
        CommentAuthorResponse author,
        String content,
        Long parentCommentId,
        Instant createdAt,
        Instant updatedAt,
        List<CommentResponse> replies
) {

    public static CommentResponse from(Comment comment, List<CommentResponse> replies) {
        return new CommentResponse(
                comment.getId(),
                comment.getVideoId(),
                new CommentAuthorResponse(
                        comment.getAuthorUserId(),
                        comment.getAuthorUsername(),
                        comment.getAuthorDisplayName()
                ),
                comment.getContent(),
                comment.getParentCommentId(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                replies
        );
    }

    public static CommentResponse from(Comment comment) {
        return from(comment, List.of());
    }
}
