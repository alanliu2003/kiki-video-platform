package com.kiki.video.api.danmaku.realtime;

import com.kiki.video.api.danmaku.dto.DanmakuResponse;
import com.kiki.video.api.danmaku.ws.DanmakuProtocol;
import com.kiki.video.api.danmaku.ws.DanmakuRoomRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class DanmakuFanout {

    private static final Logger log = LoggerFactory.getLogger(DanmakuFanout.class);

    private final DanmakuRoomRegistry rooms;
    private final ObjectMapper objectMapper;

    public DanmakuFanout(DanmakuRoomRegistry rooms, ObjectMapper objectMapper) {
        this.rooms = rooms;
        this.objectMapper = objectMapper;
    }

    public void broadcastLocal(DanmakuResponse danmaku) {
        try {
            rooms.broadcast(danmaku.videoId(), objectMapper.writeValueAsString(DanmakuProtocol.danmaku(danmaku)));
        } catch (JacksonException ex) {
            log.error("Failed to serialize danmaku broadcast danmakuId={} videoId={}",
                    danmaku.id(), danmaku.videoId(), ex);
        }
    }
}
