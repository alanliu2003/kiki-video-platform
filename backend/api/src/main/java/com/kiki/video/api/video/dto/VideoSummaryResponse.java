package com.kiki.video.api.video.dto;

import com.kiki.video.api.video.model.Video;

import java.time.Instant;

public record VideoSummaryResponse(
        Long id,
        String title,
        String status,
        long fileSizeBytes,
        Instant createdAt
) {

    public static VideoSummaryResponse from(Video video) {
        return new VideoSummaryResponse(
                video.getId(),
                video.getTitle(),
                video.getStatus().name(),
                video.getFileSizeBytes(),
                video.getCreatedAt()
        );
    }
}
