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
    }
}
