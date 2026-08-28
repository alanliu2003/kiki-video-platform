package com.kiki.video.api.upload.model;

import java.time.Instant;
import java.util.UUID;

public class UploadChunk {

    private UUID uploadSessionId;
    private int chunkIndex;
    private long chunkSizeBytes;
    private String chunkSha256;
    private Instant createdAt;

    public UUID getUploadSessionId() {
        return uploadSessionId;
    }

    public void setUploadSessionId(UUID uploadSessionId) {
        this.uploadSessionId = uploadSessionId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public long getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public void setChunkSizeBytes(long chunkSizeBytes) {
        this.chunkSizeBytes = chunkSizeBytes;
    }

    public String getChunkSha256() {
        return chunkSha256;
    }

    public void setChunkSha256(String chunkSha256) {
        this.chunkSha256 = chunkSha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
