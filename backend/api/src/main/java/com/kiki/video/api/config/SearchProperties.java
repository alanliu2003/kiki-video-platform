package com.kiki.video.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.search")
public record SearchProperties(
        Duration outboxPollInterval,
        Duration stalePublishingAfter,
        int outboxBatchSize,
        int rebuildBatchSize,
        boolean rebuild
) {
}
