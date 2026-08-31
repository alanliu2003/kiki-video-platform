package com.kiki.video.api.recommendation.model;

public class CreatorAffinityRow {

    private Long creatorId;
    private long likeInteractions;
    private long favoriteInteractions;
    private long commentInteractions;
    private long viewInteractions;

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public long getLikeInteractions() {
        return likeInteractions;
    }

    public void setLikeInteractions(long likeInteractions) {
        this.likeInteractions = likeInteractions;
    }

    public long getFavoriteInteractions() {
        return favoriteInteractions;
    }

    public void setFavoriteInteractions(long favoriteInteractions) {
        this.favoriteInteractions = favoriteInteractions;
    }

    public long getCommentInteractions() {
        return commentInteractions;
    }

    public void setCommentInteractions(long commentInteractions) {
        this.commentInteractions = commentInteractions;
    }

    public long getViewInteractions() {
        return viewInteractions;
    }

    public void setViewInteractions(long viewInteractions) {
        this.viewInteractions = viewInteractions;
    }

    public double affinityPoints() {
        return likeInteractions * 2.0
                + favoriteInteractions * 3.0
                + commentInteractions * 2.0
                + viewInteractions * 1.0;
    }
}
