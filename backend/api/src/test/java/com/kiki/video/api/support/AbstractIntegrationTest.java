package com.kiki.video.api.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestContainer.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresTestContainer.POSTGRES::getUsername);
        registry.add("spring.datasource.password", PostgresTestContainer.POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "test-jwt-secret-that-is-at-least-32-bytes-long");
        registry.add("app.jwt.access-token-ttl", () -> "1h");
        registry.add("app.minio.endpoint", MinioTestContainer::endpoint);
        registry.add("app.minio.access-key", MinioTestContainer::accessKey);
        registry.add("app.minio.secret-key", MinioTestContainer::secretKey);
        registry.add("app.minio.video-bucket", () -> "videos");
        registry.add("app.video.max-upload-size", () -> "250MB");
        registry.add("app.video.max-file-size", () -> "32MB");
        registry.add("app.video.chunk-size", () -> "256KB");
        registry.add("app.video.session-ttl", () -> "24h");
        registry.add("app.video.cleanup-interval", () -> "1h");
        registry.add("app.media.max-attempts", () -> "3");
        registry.add("app.media.outbox-poll-interval", () -> "1h");
        registry.add("app.media.stale-publishing-after", () -> "1m");
        registry.add("app.media.outbox-batch-size", () -> "20");
        registry.add("app.rocketmq.enabled", () -> "false");
        registry.add("app.rocketmq.namesrv-addr", () -> "127.0.0.1:9876");
        registry.add("app.rocketmq.media-topic", () -> "media-processing");
        registry.add("app.rocketmq.producer-group", () -> "kiki-media-api-test");
        registry.add("app.rocketmq.consumer-group", () -> "kiki-media-worker-test");
        registry.add("app.interaction.ttl", () -> "10m");
        registry.add("app.interaction.comment-rate-limit", () -> "20");
        registry.add("app.interaction.comment-rate-window", () -> "1m");
        registry.add("spring.data.redis.host", RedisTestContainer::host);
        registry.add("spring.data.redis.port", () -> String.valueOf(RedisTestContainer.port()));
        registry.add("spring.data.redis.timeout", () -> "200ms");
    }
}
