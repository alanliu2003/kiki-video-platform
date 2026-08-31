package com.kiki.video.api.search.model;

import com.kiki.video.api.search.index.VideoSearchDocument;

import java.time.Instant;

public class SearchVideoRow {

    private Long videoId;
    private String title;
    private String description;
    private Long ownerId;
    private String ownerUsername;
    private String ownerDisplayName;
    private String status;
    private String processingStatus;
    private Instant createdAt;
    private Double durationSeconds;
    private Boolean thumbnailAvailable;

    public VideoSearchDocument toDocument() {
        return new VideoSearchDocument(
                videoId,
                title,
                description == null ? "" : description,
                ownerId,
                ownerUsername,
                ownerDisplayName,
                status,
                processingStatus,
                createdAt,
                durationSeconds,
                Boolean.TRUE.equals(thumbnailAvailable)
        );
    }

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public String getOwnerDisplayName() {
        return ownerDisplayName;
    }

    public void setOwnerDisplayName(String ownerDisplayName) {
        this.ownerDisplayName = ownerDisplayName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Double getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Double durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Boolean getThumbnailAvailable() {
        return thumbnailAvailable;
    }

    public void setThumbnailAvailable(Boolean thumbnailAvailable) {
        this.thumbnailAvailable = thumbnailAvailable;
    }
}
