package com.kiki.video.api.config;

import org.springframework.util.StringUtils;

public enum MediaDeliveryMode {
    PROXY,
    PRESIGNED;

    public static MediaDeliveryMode from(String raw) {
        if (!StringUtils.hasText(raw)) {
            return PRESIGNED;
        }
        return MediaDeliveryMode.valueOf(raw.trim().toUpperCase());
    }

    public boolean isPresigned() {
        return this == PRESIGNED;
    }
}
