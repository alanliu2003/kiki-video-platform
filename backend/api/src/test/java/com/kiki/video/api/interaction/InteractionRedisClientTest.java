package com.kiki.video.api.interaction;

import com.kiki.video.api.interaction.cache.InteractionRedisClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InteractionRedisClientTest {

    @Test
    void readFallsBackWhenRedisThrows() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("Redis down"));
        when(redis.hasKey(anyString())).thenThrow(new RuntimeException("Redis down"));

        InteractionRedisClient client = new InteractionRedisClient(redis);

        assertThat(client.getCount("kiki:video:1:like-count")).isEmpty();
        assertThat(client.incrementRate("kiki:ratelimit:comment:1", Duration.ofMinutes(1))).isEmpty();
        assertThat(client.incrementIfPresent("kiki:video:1:like-count", Duration.ofMinutes(10))).isFalse();
    }

    @Test
    void blankCacheValueIsTreatedAsMiss() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("kiki:video:1:like-count")).thenReturn(" ");

        InteractionRedisClient client = new InteractionRedisClient(redis);

        assertThat(client.getCount("kiki:video:1:like-count")).isEmpty();
    }
}
