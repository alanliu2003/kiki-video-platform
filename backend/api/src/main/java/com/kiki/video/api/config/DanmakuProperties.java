package com.kiki.video.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.danmaku")
public record DanmakuProperties(
        Duration historyWindow,
        int maxLength,
        int rateLimit,
        Duration rateWindow,
        String redisChannel,
        Duration timestampTolerance,
        Duration legacyMaxTimestamp
) {
}
