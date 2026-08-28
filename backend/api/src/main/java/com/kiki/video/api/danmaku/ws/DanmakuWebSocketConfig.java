package com.kiki.video.api.danmaku.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class DanmakuWebSocketConfig implements WebSocketConfigurer {

    private final DanmakuWebSocketHandler handler;
    private final DanmakuHandshakeInterceptor handshakeInterceptor;

    public DanmakuWebSocketConfig(
            DanmakuWebSocketHandler handler,
            DanmakuHandshakeInterceptor handshakeInterceptor
    ) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/videos/{videoId}/danmaku")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
