package com.kiki.video.api.search.dto;

import java.time.Instant;

public record VideoSearchItemResponse(
        Long videoId,
        String title,
        String descriptionSnippet,
        SearchOwnerResponse owner,
        Instant createdAt,
        Double durationSeconds,
        String thumbnailUrl,
        String processingStatus,
        SearchHighlights highlights,
        long viewCount
) {
}
