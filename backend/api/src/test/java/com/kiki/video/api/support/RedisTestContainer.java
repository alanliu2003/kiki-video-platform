package com.kiki.video.api.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

public final class RedisTestContainer {

    @SuppressWarnings("resource")
    public static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));

    static {
        REDIS.start();
    }

    private RedisTestContainer() {
    }

    public static String host() {
        return REDIS.getHost();
    }

    public static int port() {
        return REDIS.getMappedPort(6379);
    }
}
