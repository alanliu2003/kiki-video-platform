package com.kiki.video.api.video.dto;

import com.kiki.video.api.user.model.User;
import com.kiki.video.api.video.model.Video;

public record VideoUploadResponse(
        Long id,
        String title,
        String description,
        VideoOwnerResponse owner,
        String contentType,
        long fileSizeBytes,
        String status,
        String processingStatus,
        java.time.Instant createdAt,
        long viewCount,
        Double durationSeconds
) {

    public static VideoUploadResponse from(Video video, User owner) {
        VideoResponse response = VideoResponse.from(video, owner);
        return new VideoUploadResponse(
                response.id(),
                response.title(),
                response.description(),
                response.owner(),
                response.contentType(),
                response.fileSizeBytes(),
                response.status(),
                response.processingStatus(),
                response.createdAt(),
                response.viewCount(),
                response.durationSeconds()
        );
    }
}
