package com.kiki.video.api.notification.dto;

import com.kiki.video.api.notification.model.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        boolean read,
        Instant createdAt,
        NotificationActorResponse actor,
        NotificationVideoResponse video,
        NotificationCommentResponse comment
) {
}
