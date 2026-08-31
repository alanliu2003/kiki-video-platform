package com.kiki.video.api.recommendation.dto;

import com.kiki.video.api.video.dto.VideoOwnerResponse;

import java.time.Instant;

public record RecommendationCardResponse(
        Long id,
        String title,
        VideoOwnerResponse owner,
        Instant createdAt,
        Double durationSeconds,
        String thumbnailUrl,
        String processingStatus,
        long viewCount,
        long likeCount,
        String recommendationReason
) {
}
