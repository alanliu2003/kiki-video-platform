package com.kiki.video.api.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.elasticsearch.enabled=false")
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
        registry.add("app.views.qualify-seconds", () -> "10");
        registry.add("app.views.qualify-percent", () -> "0.25");
        registry.add("app.views.dedupe-ttl", () -> "30m");
        registry.add("app.views.trending-cache-ttl", () -> "1ms");
        registry.add("app.views.max-page-size", () -> "500");
        registry.add("app.views.trending-view-weight", () -> "3");
        registry.add("app.views.trending-like-weight", () -> "2");
        registry.add("app.views.trending-favorite-weight", () -> "2");
        registry.add("app.views.trending-comment-weight", () -> "1.5");
        registry.add("app.views.trending-age-decay", () -> "0.02");
        registry.add("app.recommendations.cache-ttl", () -> "10m");
        registry.add("app.recommendations.max-page-size", () -> "50");
        registry.add("app.recommendations.candidate-limit", () -> "200");
        registry.add("app.recommendations.source-limit", () -> "50");
        registry.add("app.recommendations.history-limit", () -> "200");
        registry.add("app.recommendations.affinity-creator-limit", () -> "20");
        registry.add("app.recommendations.heavy-seen-threshold", () -> "3");
        registry.add("app.recommendations.affinity-weight", () -> "4");
        registry.add("app.recommendations.followed-weight", () -> "3");
        registry.add("app.recommendations.view-weight", () -> "1.5");
        registry.add("app.recommendations.like-weight", () -> "1.2");
        registry.add("app.recommendations.favorite-weight", () -> "1.5");
        registry.add("app.recommendations.comment-weight", () -> "0.8");
        registry.add("app.recommendations.freshness-hours", () -> "48");
        registry.add("app.recommendations.freshness-weight", () -> "0.05");
        registry.add("app.recommendations.seen-penalty", () -> "4");
        registry.add("app.recommendations.heavy-seen-penalty", () -> "10");
        registry.add("app.danmaku.history-window", () -> "60s");
        registry.add("app.danmaku.max-length", () -> "200");
        registry.add("app.danmaku.rate-limit", () -> "10");
        registry.add("app.danmaku.rate-window", () -> "10s");
        registry.add("app.danmaku.redis-channel", () -> "kiki:danmaku");
        registry.add("app.danmaku.timestamp-tolerance", () -> "2s");
        registry.add("app.danmaku.legacy-max-timestamp", () -> "6h");
        registry.add("spring.data.redis.host", RedisTestContainer::host);
        registry.add("spring.data.redis.port", () -> String.valueOf(RedisTestContainer.port()));
        registry.add("spring.data.redis.timeout", () -> "200ms");
        registry.add("app.search.outbox-poll-interval", () -> "1h");
        registry.add("app.search.stale-publishing-after", () -> "1m");
        registry.add("app.search.outbox-batch-size", () -> "20");
        registry.add("app.search.rebuild-batch-size", () -> "50");
        registry.add("app.search.rebuild", () -> "false");
    }
}
