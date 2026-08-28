package com.kiki.video.api.danmaku.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DanmakuRoomRegistry {

    private static final Logger log = LoggerFactory.getLogger(DanmakuRoomRegistry.class);

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    public void add(long videoId, WebSocketSession session) {
        rooms.computeIfAbsent(videoId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("Danmaku socket connected videoId={} sessionId={} localViewers={}",
                videoId, session.getId(), count(videoId));
    }

    public void remove(long videoId, WebSocketSession session) {
        Set<WebSocketSession> sessions = rooms.get(videoId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            rooms.remove(videoId, sessions);
        }
        log.info("Danmaku socket disconnected videoId={} sessionId={} localViewers={}",
                videoId, session.getId(), count(videoId));
    }

    public void remove(WebSocketSession session) {
        Long videoId = DanmakuSessionKeys.videoId(session);
        if (videoId != null) {
            remove(videoId, session);
        }
    }

    public int count(long videoId) {
        Set<WebSocketSession> sessions = rooms.get(videoId);
        return sessions == null ? 0 : sessions.size();
    }

    public void broadcast(long videoId, String payload) {
        Set<WebSocketSession> sessions = rooms.get(videoId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(new org.springframework.web.socket.TextMessage(payload));
                }
            } catch (IOException | RuntimeException ex) {
                log.warn("Failed to send danmaku to sessionId={} videoId={}", session.getId(), videoId, ex);
                closeQuietly(session);
                sessions.remove(session);
            }
        }
        if (sessions.isEmpty()) {
            rooms.remove(videoId, sessions);
        }
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
