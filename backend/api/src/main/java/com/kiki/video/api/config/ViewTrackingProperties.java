package com.kiki.video.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.views")
public record ViewTrackingProperties(
        double qualifySeconds,
        double qualifyPercent,
        Duration dedupeTtl,
        Duration trendingCacheTtl,
        int maxPageSize,
        double trendingViewWeight,
        double trendingLikeWeight,
        double trendingFavoriteWeight,
        double trendingCommentWeight,
        double trendingAgeDecay
) {
}
