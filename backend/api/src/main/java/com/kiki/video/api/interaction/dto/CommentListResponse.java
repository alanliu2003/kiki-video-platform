package com.kiki.video.api.interaction.dto;

import java.util.List;

public record CommentListResponse(
        List<CommentResponse> items,
        int page,
        int size,
        long total
) {
}
