package com.kiki.video.api.search.index;

import java.time.Instant;

public record VideoSearchDocument(
        Long videoId,
        String title,
        String description,
        Long ownerId,
        String ownerUsername,
        String ownerDisplayName,
        String status,
        String processingStatus,
        Instant createdAt,
        Double durationSeconds,
        Boolean thumbnailAvailable
) {
}
