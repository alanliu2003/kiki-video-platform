package com.kiki.video.api.danmaku.ws;

import com.kiki.video.api.danmaku.dto.DanmakuResponse;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DanmakuProtocol {

    public static final int VERSION = 1;
    public static final String AUTH = "AUTH";
    public static final String DANMAKU_SEND = "DANMAKU_SEND";
    public static final String AUTH_OK = "AUTH_OK";
    public static final String DANMAKU = "DANMAKU";
    public static final String DANMAKU_ACK = "DANMAKU_ACK";
    public static final String ERROR = "ERROR";

    public static final String AUTH_REQUIRED = "DANMAKU_AUTH_REQUIRED";
    public static final String INVALID_CONTENT = "DANMAKU_INVALID_CONTENT";
    public static final String INVALID_TIMESTAMP = "DANMAKU_INVALID_TIMESTAMP";
    public static final String RATE_LIMITED = "DANMAKU_RATE_LIMITED";
    public static final String VIDEO_NOT_FOUND = "VIDEO_NOT_FOUND";
    public static final String INTERNAL_ERROR = "DANMAKU_INTERNAL_ERROR";
    public static final String INVALID_MESSAGE = "DANMAKU_INVALID_MESSAGE";
    public static final String AUTH_FAILED = "DANMAKU_AUTH_FAILED";

    private DanmakuProtocol() {
    }

    public static Map<String, Object> authOk() {
        Map<String, Object> message = base(AUTH_OK);
        return message;
    }

    public static Map<String, Object> danmaku(DanmakuResponse danmaku) {
        Map<String, Object> message = base(DANMAKU);
        message.put("danmaku", danmaku);
        return message;
    }

    public static Map<String, Object> ack(String clientMessageId, Long danmakuId) {
        Map<String, Object> message = base(DANMAKU_ACK);
        message.put("clientMessageId", clientMessageId);
        message.put("danmakuId", danmakuId);
        return message;
    }

    public static Map<String, Object> error(String code, String text) {
        Map<String, Object> message = base(ERROR);
        message.put("code", code);
        message.put("message", text);
        return message;
    }

    private static Map<String, Object> base(String type) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("v", VERSION);
        message.put("type", type);
        return message;
    }
}
