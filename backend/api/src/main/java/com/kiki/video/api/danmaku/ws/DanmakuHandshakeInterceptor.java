package com.kiki.video.api.danmaku.ws;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DanmakuHandshakeInterceptor implements HandshakeInterceptor {

    private static final Pattern PATH = Pattern.compile("^/ws/videos/(\\d+)/danmaku$");

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        Matcher matcher = PATH.matcher(request.getURI().getPath());
        if (!matcher.matches()) {
            return false;
        }
        attributes.put(DanmakuSessionKeys.VIDEO_ID, Long.parseLong(matcher.group(1)));
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }
}
