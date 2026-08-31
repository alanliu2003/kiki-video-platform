package com.kiki.video.api.video.dto;

import com.kiki.video.api.video.model.Video;
import com.kiki.video.common.media.MediaProcessingStatus;

import java.time.Instant;

public record VideoSummaryResponse(
        Long id,
        String title,
        String status,
        String processingStatus,
        long fileSizeBytes,
        Instant createdAt,
        long viewCount
) {

    public static VideoSummaryResponse from(Video video) {
        MediaProcessingStatus processing = video.getProcessingStatus() == null
                ? MediaProcessingStatus.NOT_REQUESTED
                : video.getProcessingStatus();
        return new VideoSummaryResponse(
                video.getId(),
                video.getTitle(),
                video.getStatus().name(),
                processing.name(),
                video.getFileSizeBytes(),
                video.getCreatedAt(),
                video.getViewCount()
        );
    }
}
