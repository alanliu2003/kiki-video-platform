package com.kiki.video.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.minio")
public record WorkerMinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String videoBucket
) {
}
