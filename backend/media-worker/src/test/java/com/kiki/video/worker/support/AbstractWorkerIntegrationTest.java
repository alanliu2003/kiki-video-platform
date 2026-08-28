package com.kiki.video.worker.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractWorkerIntegrationTest {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestContainer.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresTestContainer.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresTestContainer.POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("app.minio.endpoint", MinioTestContainer::endpoint);
        registry.add("app.minio.access-key", MinioTestContainer::accessKey);
        registry.add("app.minio.secret-key", MinioTestContainer::secretKey);
        registry.add("app.minio.video-bucket", () -> "videos");
        registry.add("app.rocketmq.enabled", () -> "false");
        registry.add("app.media.verify-ffmpeg", () -> "false");
        registry.add("app.media.max-attempts", () -> "3");
        registry.add("app.media.timeout", () -> "2m");
        registry.add("app.media.hls-segment-duration", () -> "6");
        registry.add("app.media.stale-processing-after", () -> "5m");
        registry.add("app.media.retry-backoff", () -> "1s");
        registry.add("app.media.ffmpeg-path", () -> "ffmpeg");
        registry.add("app.media.ffprobe-path", () -> "ffprobe");
        registry.add("server.port", () -> "0");
    }
}
