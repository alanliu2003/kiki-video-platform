package com.kiki.video.api.video.dto;

public record PlaybackResponse(
        String status,
        String type,
        String manifestUrl,
        String contentUrl,
        String thumbnailUrl
) {
}
