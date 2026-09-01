package com.kiki.video.api.observability.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component("redis")
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory connectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Health health() {
        try (var connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            if (pong == null || pong.isBlank()) {
                return DependencyHealth.degraded("redis", "empty ping response");
            }
            return DependencyHealth.up("redis");
        } catch (RuntimeException ex) {
            return DependencyHealth.degraded("redis", ex.getMessage());
        }
    }
}
