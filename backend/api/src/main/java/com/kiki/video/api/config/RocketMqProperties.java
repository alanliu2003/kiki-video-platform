package com.kiki.video.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rocketmq")
public record RocketMqProperties(
        boolean enabled,
        String namesrvAddr,
        String mediaTopic,
        String producerGroup,
        String consumerGroup
) {
}
