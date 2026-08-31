package com.kiki.video.api.danmaku.dto;

import com.kiki.video.api.danmaku.model.Danmaku;

import java.time.Instant;

public record DanmakuResponse(
        Long id,
        Long videoId,
        DanmakuUserResponse user,
        String content,
        long videoTimeMs,
        String style,
        Instant createdAt
) {

    public static DanmakuResponse from(Danmaku danmaku) {
        return new DanmakuResponse(
                danmaku.getId(),
                danmaku.getVideoId(),
                new DanmakuUserResponse(
                        danmaku.getUserId(),
                        danmaku.getUsername(),
                        danmaku.getDisplayName()
                ),
                danmaku.getContent(),
                danmaku.getVideoTimeMs(),
                danmaku.getStyle() == null ? "NORMAL" : danmaku.getStyle().name(),
                danmaku.getCreatedAt()
        );
    }
}
