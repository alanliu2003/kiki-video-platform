package com.kiki.video.api.view.dto;

public record QualifyViewRequest(
        Long watchedMs,
        Long durationMs,
        String clientViewId
) {
}
