package com.kiki.video.api.search.index;

import com.kiki.video.api.search.dto.VideoSearchSort;

import java.time.Instant;

public record VideoSearchQuery(
        String q,
        int page,
        int size,
        VideoSearchSort sort,
        Long ownerId,
        String processingStatus,
        Instant createdAfter,
        Instant createdBefore
) {
}
