package com.kiki.video.api.notification.dto;

import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> items,
        int page,
        int size,
        long total
) {
}
