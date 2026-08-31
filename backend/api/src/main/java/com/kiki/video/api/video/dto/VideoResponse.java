package com.kiki.video.api.video.dto;

import com.kiki.video.api.user.model.User;
import com.kiki.video.api.video.model.Video;
import com.kiki.video.common.media.MediaProcessingStatus;

import java.time.Instant;

public record VideoResponse(
        Long id,
        String title,
        String description,
        VideoOwnerResponse owner,
        String contentType,
        long fileSizeBytes,
        String status,
        String processingStatus,
        Instant createdAt,
        long viewCount,
        Double durationSeconds
) {

    public static VideoResponse from(Video video, User owner) {
        MediaProcessingStatus processing = video.getProcessingStatus() == null
                ? MediaProcessingStatus.NOT_REQUESTED
                : video.getProcessingStatus();
        return new VideoResponse(
                video.getId(),
                video.getTitle(),
                video.getDescription(),
                VideoOwnerResponse.from(owner),
                video.getContentType(),
                video.getFileSizeBytes(),
                video.getStatus().name(),
                processing.name(),
                video.getCreatedAt(),
                video.getViewCount(),
                video.getDurationSeconds()
        );
    }
}
