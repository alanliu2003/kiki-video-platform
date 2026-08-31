package com.kiki.video.api.recommendation.dto;

import java.util.List;

public record RecommendationFeedResponse(
        List<RecommendationCardResponse> items,
        int page,
        int size,
        long total,
        boolean coldStart
) {
}
