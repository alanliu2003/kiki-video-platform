package com.kiki.video.api.danmaku.model;

import java.time.Instant;

public class Danmaku {

    private Long id;
    private Long videoId;
    private Long userId;
    private String content;
    private long videoTimeMs;
    private DanmakuStyle style;
    private DanmakuStatus status;
    private String clientMessageId;
    private Instant createdAt;
    private String username;
    private String displayName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getVideoTimeMs() {
        return videoTimeMs;
    }

    public void setVideoTimeMs(long videoTimeMs) {
        this.videoTimeMs = videoTimeMs;
    }

    public DanmakuStyle getStyle() {
        return style;
    }

    public void setStyle(DanmakuStyle style) {
        this.style = style;
    }

    public DanmakuStatus getStatus() {
        return status;
    }

    public void setStatus(DanmakuStatus status) {
        this.status = status;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
