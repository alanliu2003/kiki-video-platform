package com.kiki.video.api.danmaku.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
public class DanmakuRedisSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(DanmakuRedisSubscriber.class);

    private final ObjectMapper objectMapper;
    private final DanmakuFanout fanout;

    public DanmakuRedisSubscriber(ObjectMapper objectMapper, DanmakuFanout fanout) {
        this.objectMapper = objectMapper;
        this.fanout = fanout;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            DanmakuLiveEvent event = objectMapper.readValue(payload, DanmakuLiveEvent.class);
            if (event == null || event.danmaku() == null) {
                log.warn("Ignoring empty danmaku Redis event");
                return;
            }
            fanout.broadcastLocal(event.danmaku());
        } catch (RuntimeException ex) {
            log.warn("Failed to dispatch danmaku Redis event", ex);
        }
    }
}
