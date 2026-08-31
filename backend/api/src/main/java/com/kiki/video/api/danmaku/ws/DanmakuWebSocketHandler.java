package com.kiki.video.api.danmaku.ws;

import com.kiki.video.api.auth.jwt.InvalidAccessTokenException;
import com.kiki.video.api.auth.jwt.JwtPayload;
import com.kiki.video.api.auth.jwt.JwtService;
import com.kiki.video.api.danmaku.dto.DanmakuSubmitResult;
import com.kiki.video.api.danmaku.realtime.DanmakuRedisPublisher;
import com.kiki.video.api.danmaku.service.DanmakuService;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class DanmakuWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(DanmakuWebSocketHandler.class);

    private final DanmakuRoomRegistry rooms;
    private final DanmakuService danmakuService;
    private final JwtService jwtService;
    private final DanmakuRedisPublisher publisher;
    private final ObjectMapper objectMapper;

    public DanmakuWebSocketHandler(
            DanmakuRoomRegistry rooms,
            DanmakuService danmakuService,
            JwtService jwtService,
            DanmakuRedisPublisher publisher,
            ObjectMapper objectMapper
    ) {
        this.rooms = rooms;
        this.danmakuService = danmakuService;
        this.jwtService = jwtService;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long videoId = DanmakuSessionKeys.videoId(session);
        if (videoId == null || !danmakuService.videoExists(videoId)) {
            closeQuietly(session, CloseStatus.BAD_DATA);
            return;
        }
        rooms.add(videoId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (RuntimeException ex) {
            send(session, DanmakuProtocol.error(DanmakuProtocol.INVALID_MESSAGE, "Message must be JSON"));
            return;
        }
        if (node == null || !node.isObject()) {
            send(session, DanmakuProtocol.error(DanmakuProtocol.INVALID_MESSAGE, "Message must be a JSON object"));
            return;
        }
        String type = text(node, "type");
        if (DanmakuProtocol.AUTH.equals(type)) {
            handleAuth(session, node);
            return;
        }
        if (DanmakuProtocol.DANMAKU_SEND.equals(type)) {
            handleSend(session, node);
            return;
        }
        send(session, DanmakuProtocol.error(DanmakuProtocol.INVALID_MESSAGE, "Unknown message type"));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long videoId = DanmakuSessionKeys.videoId(session);
        log.warn("Danmaku transport error videoId={} sessionId={}", videoId, session.getId(), exception);
        rooms.remove(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        rooms.remove(session);
    }

    private void handleAuth(WebSocketSession session, JsonNode node) {
        String token = text(node, "token");
        if (token == null || token.isBlank()) {
            send(session, DanmakuProtocol.error(DanmakuProtocol.AUTH_FAILED, "Token is required"));
            return;
        }
        try {
            JwtPayload payload = jwtService.parse(token);
            session.getAttributes().put(DanmakuSessionKeys.USER_ID, payload.userId());
            log.info("Danmaku auth succeeded videoId={} userId={} sessionId={}",
                    DanmakuSessionKeys.videoId(session), payload.userId(), session.getId());
            send(session, DanmakuProtocol.authOk());
        } catch (InvalidAccessTokenException ex) {
            log.info("Danmaku auth failed videoId={} sessionId={}",
                    DanmakuSessionKeys.videoId(session), session.getId());
            send(session, DanmakuProtocol.error(DanmakuProtocol.AUTH_FAILED, "Invalid access token"));
        }
    }

    private void handleSend(WebSocketSession session, JsonNode node) {
        Long userId = DanmakuSessionKeys.userId(session);
        if (userId == null) {
            send(session, DanmakuProtocol.error(DanmakuProtocol.AUTH_REQUIRED, "Authentication is required to send danmaku"));
            return;
        }
        Long videoId = DanmakuSessionKeys.videoId(session);
        String clientMessageId = text(node, "clientMessageId");
        String content = text(node, "content");
        Long videoTimeMs = longValue(node, "videoTimeMs");
        try {
            DanmakuSubmitResult result = danmakuService.submit(videoId, userId, clientMessageId, content, videoTimeMs);
            if (result.created()) {
                log.info("Danmaku persisted videoId={} userId={} danmakuId={}",
                        videoId, userId, result.danmaku().id());
                publisher.publishOrFallback(result.danmaku());
            }
            send(session, DanmakuProtocol.ack(clientMessageId, result.danmaku().id()));
        } catch (ApiException ex) {
            send(session, DanmakuProtocol.error(toWsCode(ex.getCode()), ex.getMessage()));
        } catch (RuntimeException ex) {
            log.error("Danmaku send failed videoId={} userId={}", videoId, userId, ex);
            send(session, DanmakuProtocol.error(DanmakuProtocol.INTERNAL_ERROR, "Unable to send danmaku"));
        }
    }

    private static String toWsCode(ErrorCode code) {
        return switch (code) {
            case UNAUTHORIZED, USER_NOT_FOUND -> DanmakuProtocol.AUTH_REQUIRED;
            case INVALID_DANMAKU -> DanmakuProtocol.INVALID_CONTENT;
            case INVALID_DANMAKU_TIMESTAMP -> DanmakuProtocol.INVALID_TIMESTAMP;
            case RATE_LIMITED -> DanmakuProtocol.RATE_LIMITED;
            case VIDEO_NOT_FOUND -> DanmakuProtocol.VIDEO_NOT_FOUND;
            default -> DanmakuProtocol.INTERNAL_ERROR;
        };
    }

    private void send(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException | RuntimeException ex) {
            log.warn("Failed to write WebSocket message sessionId={}", session.getId(), ex);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asString();
    }

    private static Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isNumber()) {
            return null;
        }
        return value.asLong();
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
            // already closed
        }
    }
}
