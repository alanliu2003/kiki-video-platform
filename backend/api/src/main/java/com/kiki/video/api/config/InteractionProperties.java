package com.kiki.video.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.interaction")
public record InteractionProperties(
        Duration ttl,
        int commentRateLimit,
        Duration commentRateWindow
) {
}
