package com.kiki.video.api.recommendation.model;

public class QualifiedViewRow {

    private Long videoId;
    private int qualifiedViewCount;

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public int getQualifiedViewCount() {
        return qualifiedViewCount;
    }

    public void setQualifiedViewCount(int qualifiedViewCount) {
        this.qualifiedViewCount = qualifiedViewCount;
    }
}
