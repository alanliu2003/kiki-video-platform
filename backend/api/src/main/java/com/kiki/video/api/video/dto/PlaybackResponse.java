package com.kiki.video.api.video.dto;

import java.time.Instant;

public record PlaybackResponse(
        String status,
        String type,
        String mode,
        String url,
        Instant expiresAt,
        String fallbackUrl,
        String processingStatus,
        String deliveryMode,
        String manifestUrl,
        String contentUrl,
        String thumbnailUrl
) {
}
