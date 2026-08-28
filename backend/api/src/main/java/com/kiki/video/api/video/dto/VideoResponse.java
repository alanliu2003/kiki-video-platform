package com.kiki.video.api.video.dto;

import com.kiki.video.api.user.model.User;
import com.kiki.video.api.video.model.Video;

import java.time.Instant;

public record VideoResponse(
        Long id,
        String title,
        String description,
        VideoOwnerResponse owner,
        String contentType,
        long fileSizeBytes,
        String status,
        Instant createdAt
) {

    public static VideoResponse from(Video video, User owner) {
        return new VideoResponse(
                video.getId(),
                video.getTitle(),
                video.getDescription(),
                VideoOwnerResponse.from(owner),
                video.getContentType(),
                video.getFileSizeBytes(),
                video.getStatus().name(),
                video.getCreatedAt()
        );
    }
}
