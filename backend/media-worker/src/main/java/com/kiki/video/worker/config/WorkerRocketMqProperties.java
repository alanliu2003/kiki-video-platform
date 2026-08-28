package com.kiki.video.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rocketmq")
public record WorkerRocketMqProperties(
        boolean enabled,
        String namesrvAddr,
        String mediaTopic,
        String consumerGroup
) {
}
