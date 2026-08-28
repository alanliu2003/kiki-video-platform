package com.kiki.video.api.danmaku.ws;

import org.springframework.web.socket.WebSocketSession;

public final class DanmakuSessionKeys {

    public static final String VIDEO_ID = "videoId";
    public static final String USER_ID = "userId";

    private DanmakuSessionKeys() {
    }

    public static Long videoId(WebSocketSession session) {
        Object value = session.getAttributes().get(VIDEO_ID);
        return value instanceof Long id ? id : null;
    }

    public static Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get(USER_ID);
        return value instanceof Long id ? id : null;
    }
}
