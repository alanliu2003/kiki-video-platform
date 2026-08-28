package com.kiki.video.api.interaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank(message = "Content is required")
        @Size(max = 2000, message = "Content must be at most 2000 characters")
        String content,
        Long parentCommentId
) {
}
