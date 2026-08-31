package com.kiki.video.api.danmaku;

import com.kiki.video.api.danmaku.ws.DanmakuRoomRegistry;
import com.kiki.video.api.danmaku.ws.DanmakuSessionKeys;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DanmakuRoomRegistryTest {

    @Test
    void addRemoveAndBroadcastStayScopedToVideo() throws Exception {
        DanmakuRoomRegistry registry = new DanmakuRoomRegistry();
        WebSocketSession a1 = session("a1", true);
        WebSocketSession a2 = session("a2", true);
        WebSocketSession b1 = session("b1", true);

        registry.add(7, a1);
        registry.add(7, a2);
        registry.add(8, b1);
        assertThat(registry.count(7)).isEqualTo(2);

        registry.broadcast(7, "{\"type\":\"DANMAKU\"}");
        verify(a1).sendMessage(any(TextMessage.class));
        verify(a2).sendMessage(any(TextMessage.class));
        verify(b1, never()).sendMessage(any(TextMessage.class));

        registry.remove(7, a1);
        assertThat(registry.count(7)).isEqualTo(1);
        registry.remove(a2);
        assertThat(registry.count(7)).isZero();
    }

    @Test
    void closedAndFailedSessionsAreDropped() throws Exception {
        DanmakuRoomRegistry registry = new DanmakuRoomRegistry();
        WebSocketSession closed = session("closed", false);
        WebSocketSession broken = session("broken", true);
        doThrow(new IOException("gone")).when(broken).sendMessage(any(TextMessage.class));

        registry.add(3, closed);
        registry.add(3, broken);
        registry.broadcast(3, "{}");
        assertThat(registry.count(3)).isZero();
    }

    private static WebSocketSession session(String id, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        attributes.put(DanmakuSessionKeys.VIDEO_ID, 7L);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(open);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }
}
