package com.kiki.video.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String videoBucket,
        String publicEndpoint
) {
    public String signingEndpoint() {
        if (publicEndpoint == null || publicEndpoint.isBlank()) {
            return endpoint;
        }
        return publicEndpoint;
    }
}
