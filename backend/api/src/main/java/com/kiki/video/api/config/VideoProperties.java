package com.kiki.video.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.video")
public record VideoProperties(
        DataSize maxUploadSize,
        DataSize maxFileSize,
        DataSize chunkSize,
        Duration sessionTtl,
        Duration cleanupInterval
) {

    public long maxUploadSizeBytes() {
        return maxUploadSize.toBytes();
    }

    public long maxFileSizeBytes() {
        return maxFileSize.toBytes();
    }

    public long chunkSizeBytes() {
        return chunkSize.toBytes();
    }
}
