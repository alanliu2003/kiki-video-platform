package com.kiki.video.api.danmaku.realtime;

import com.kiki.video.api.danmaku.dto.DanmakuResponse;

public record DanmakuLiveEvent(int v, long videoId, DanmakuResponse danmaku) {

    public static DanmakuLiveEvent of(DanmakuResponse danmaku) {
        return new DanmakuLiveEvent(1, danmaku.videoId(), danmaku);
    }
}
