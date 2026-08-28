package com.kiki.video.api.danmaku.realtime;

import com.kiki.video.api.config.DanmakuProperties;
import com.kiki.video.api.danmaku.dto.DanmakuResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class DanmakuRedisPublisher {

    private static final Logger log = LoggerFactory.getLogger(DanmakuRedisPublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DanmakuProperties properties;
    private final DanmakuFanout fanout;

    public DanmakuRedisPublisher(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            DanmakuProperties properties,
            DanmakuFanout fanout
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.fanout = fanout;
    }

    public void publishOrFallback(DanmakuResponse danmaku) {
        try {
            String payload = objectMapper.writeValueAsString(DanmakuLiveEvent.of(danmaku));
            redis.convertAndSend(properties.redisChannel(), payload);
            log.info("Published danmaku to Redis videoId={} danmakuId={} userId={}",
                    danmaku.videoId(), danmaku.id(), danmaku.user().id());
        } catch (RuntimeException ex) {
            log.warn("Redis danmaku publish failed; falling back to local broadcast videoId={} danmakuId={}",
                    danmaku.videoId(), danmaku.id(), ex);
            fanout.broadcastLocal(danmaku);
        }
    }
}
