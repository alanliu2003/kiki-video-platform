package com.kiki.video.api.view.dto;

import java.util.List;

public record VideoFeedResponse(
        List<VideoCardResponse> items,
        int page,
        int size,
        long total
) {
}
