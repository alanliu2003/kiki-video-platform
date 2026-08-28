package com.kiki.video.worker.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

public final class PostgresTestContainer {

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("video_platform_worker_test")
            .withUsername("video")
            .withPassword("video");

    static {
        POSTGRES.start();
    }

    private PostgresTestContainer() {
    }
}
