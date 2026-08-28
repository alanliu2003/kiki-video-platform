package com.kiki.video.api.video.dto;

import java.util.List;

public record VideoListResponse(
        List<VideoSummaryResponse> items,
        int page,
        int size,
        long total
) {
}
