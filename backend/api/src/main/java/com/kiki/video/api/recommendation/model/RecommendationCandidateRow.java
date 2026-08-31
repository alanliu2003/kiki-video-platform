package com.kiki.video.api.recommendation.model;

import com.kiki.video.common.media.MediaProcessingStatus;

import java.time.Instant;

public class RecommendationCandidateRow {

    private Long id;
    private String title;
    private Long ownerId;
    private String ownerUsername;
    private String ownerDisplayName;
    private Instant createdAt;
    private Double durationSeconds;
    private Boolean thumbnailAvailable;
    private MediaProcessingStatus processingStatus;
    private long viewCount;
    private long likeCount;
    private long favoriteCount;
    private long commentCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public MediaProcessingStatus getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(MediaProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(long favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }
}
