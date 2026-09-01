package com.kiki.video.api.observability.health;

import com.kiki.video.api.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("minio")
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioHealthIndicator(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.videoBucket()).build()
            );
            if (!exists) {
                return DependencyHealth.down("minio", "bucket missing: " + properties.videoBucket());
            }
            return DependencyHealth.up("minio");
        } catch (Exception ex) {
            return DependencyHealth.down("minio", ex.getMessage());
        }
    }
}
