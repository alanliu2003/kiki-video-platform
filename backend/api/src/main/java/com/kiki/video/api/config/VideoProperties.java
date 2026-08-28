package com.kiki.video.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.video")
public record VideoProperties(DataSize maxUploadSize) {

    public long maxUploadSizeBytes() {
        return maxUploadSize.toBytes();
    }
}
