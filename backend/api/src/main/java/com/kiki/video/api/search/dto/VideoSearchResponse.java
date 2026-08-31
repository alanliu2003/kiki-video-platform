package com.kiki.video.api.search.dto;

import java.util.List;

public record VideoSearchResponse(
        List<VideoSearchItemResponse> items,
        int page,
        int size,
        long total,
        Long tookMs
) {
}
