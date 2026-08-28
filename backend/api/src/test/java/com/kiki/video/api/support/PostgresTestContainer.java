package com.kiki.video.api.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

public final class PostgresTestContainer {

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("video_platform_test")
            .withUsername("video")
            .withPassword("video");

    static {
        POSTGRES.start();
    }

    private PostgresTestContainer() {
    }
}
