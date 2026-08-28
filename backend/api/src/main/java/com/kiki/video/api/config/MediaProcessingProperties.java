package com.kiki.video.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.media")
public record MediaProcessingProperties(
        int maxAttempts,
        Duration outboxPollInterval,
        Duration stalePublishingAfter,
        int outboxBatchSize
) {
}
