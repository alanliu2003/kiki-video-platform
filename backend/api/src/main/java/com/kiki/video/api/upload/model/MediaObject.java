package com.kiki.video.api.upload.model;

import com.kiki.video.common.media.MediaProcessingStatus;

import java.time.Instant;

public class MediaObject {

    private Long id;
    private String sha256;
    private String objectKey;
    private long fileSizeBytes;
    private String contentType;
    private MediaProcessingStatus processingStatus;
    private int processingAttempts;
    private String processingError;
    private String processedPrefix;
    private String masterPlaylistKey;
    private String thumbnailKey;
    private Double durationSeconds;
    private Integer sourceWidth;
    private Integer sourceHeight;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant processedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public MediaProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(MediaProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public int getProcessingAttempts() {
        return processingAttempts;
    }

    public void setProcessingAttempts(int processingAttempts) {
        this.processingAttempts = processingAttempts;
    }

    public String getProcessingError() {
        return processingError;
    }

    public void setProcessingError(String processingError) {
        this.processingError = processingError;
    }

    public String getProcessedPrefix() {
        return processedPrefix;
    }

    public void setProcessedPrefix(String processedPrefix) {
        this.processedPrefix = processedPrefix;
    }

    public String getMasterPlaylistKey() {
        return masterPlaylistKey;
    }

    public void setMasterPlaylistKey(String masterPlaylistKey) {
        this.masterPlaylistKey = masterPlaylistKey;
    }

    public String getThumbnailKey() {
        return thumbnailKey;
    }

    public void setThumbnailKey(String thumbnailKey) {
        this.thumbnailKey = thumbnailKey;
    }

    public Double getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Double durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Integer getSourceWidth() {
        return sourceWidth;
    }

    public void setSourceWidth(Integer sourceWidth) {
        this.sourceWidth = sourceWidth;
    }

    public Integer getSourceHeight() {
        return sourceHeight;
    }

    public void setSourceHeight(Integer sourceHeight) {
        this.sourceHeight = sourceHeight;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
