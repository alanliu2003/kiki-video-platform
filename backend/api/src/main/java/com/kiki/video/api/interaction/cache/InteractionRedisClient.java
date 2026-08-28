package com.kiki.video.api.interaction.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class InteractionRedisClient {

    private static final Logger log = LoggerFactory.getLogger(InteractionRedisClient.class);

    private final StringRedisTemplate redis;

    public InteractionRedisClient(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Optional<Long> getCount(String key) {
        try {
            String value = redis.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(value));
        } catch (RuntimeException ex) {
            log.warn("Redis read failed for {}; falling back to PostgreSQL", key, ex);
            return Optional.empty();
        }
    }

    public void setCount(String key, long value, Duration ttl) {
        try {
            redis.opsForValue().set(key, Long.toString(Math.max(value, 0)), ttl);
        } catch (RuntimeException ex) {
            log.warn("Redis write failed for {}", key, ex);
        }
    }

    public boolean incrementIfPresent(String key, Duration ttl) {
        try {
            Boolean exists = redis.hasKey(key);
            if (!Boolean.TRUE.equals(exists)) {
                return false;
            }
            redis.opsForValue().increment(key);
            redis.expire(key, ttl);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Redis increment failed for {}; invalidating key", key, ex);
            invalidate(key);
            return false;
        }
    }

    public boolean decrementIfPresent(String key, Duration ttl) {
        try {
            Boolean exists = redis.hasKey(key);
            if (!Boolean.TRUE.equals(exists)) {
                return false;
            }
            Long next = redis.opsForValue().decrement(key);
            if (next != null && next < 0) {
                redis.opsForValue().set(key, "0", ttl);
            } else {
                redis.expire(key, ttl);
            }
            return true;
        } catch (RuntimeException ex) {
            log.warn("Redis decrement failed for {}; invalidating key", key, ex);
            invalidate(key);
            return false;
        }
    }

    public void invalidate(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Redis delete failed for {}", key, ex);
        }
    }

    public Optional<Long> incrementRate(String key, Duration window) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, window);
            }
            return Optional.ofNullable(count);
        } catch (RuntimeException ex) {
            log.warn("Redis rate-limit increment failed; failing open", ex);
            return Optional.empty();
        }
    }
}
