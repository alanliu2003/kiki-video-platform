package com.kiki.video.api.view.dto;

import com.kiki.video.api.video.dto.VideoOwnerResponse;

import java.time.Instant;

public record VideoCardResponse(
        Long id,
        String title,
        VideoOwnerResponse owner,
        Instant createdAt,
        Double durationSeconds,
        String thumbnailUrl,
        String processingStatus,
        long viewCount,
        long likeCount
) {
}
