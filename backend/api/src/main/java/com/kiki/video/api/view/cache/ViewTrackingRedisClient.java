package com.kiki.video.api.view.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class ViewTrackingRedisClient {

    private static final Logger log = LoggerFactory.getLogger(ViewTrackingRedisClient.class);

    private final StringRedisTemplate redis;

    public ViewTrackingRedisClient(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Fail-open viewer dedupe. Returns {@code true} when this viewer may increment
     * (new claim, or Redis unavailable). Returns {@code false} only when Redis
     * already holds the key for this viewer/video.
     */
    public boolean tryClaim(String key, Duration ttl) {
        try {
            Boolean claimed = redis.opsForValue().setIfAbsent(key, "1", ttl);
            return !Boolean.FALSE.equals(claimed);
        } catch (RuntimeException ex) {
            log.warn("Redis view-dedupe claim failed for {}; failing open", key, ex);
            return true;
        }
    }

    public Optional<String> get(String key) {
        try {
            String value = redis.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (RuntimeException ex) {
            log.warn("Redis trending cache read failed for {}; falling back to PostgreSQL", key, ex);
            return Optional.empty();
        }
    }

    public void set(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (RuntimeException ex) {
            log.warn("Redis trending cache write failed for {}", key, ex);
        }
    }
}
