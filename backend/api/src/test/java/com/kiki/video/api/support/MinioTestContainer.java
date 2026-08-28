package com.kiki.video.api.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public final class MinioTestContainer {

    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";

    @SuppressWarnings("resource")
    public static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));

    static {
        MINIO.start();
    }

    private MinioTestContainer() {
    }

    public static String endpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    public static String accessKey() {
        return ACCESS_KEY;
    }

    public static String secretKey() {
        return SECRET_KEY;
    }
}
