package com.kiki.video.api.view.dto;

public record QualifyViewResponse(
        boolean counted,
        boolean alreadyCounted,
        long viewCount
) {
}
