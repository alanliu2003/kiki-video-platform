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
    }
}
