package com.kiki.video.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.recommendations")
public record RecommendationProperties(
        Duration cacheTtl,
        int maxPageSize,
        int candidateLimit,
        int sourceLimit,
        int historyLimit,
        int affinityCreatorLimit,
        int heavySeenThreshold,
        double affinityWeight,
        double followedWeight,
        double viewWeight,
        double likeWeight,
        double favoriteWeight,
        double commentWeight,
        double freshnessHours,
        double freshnessWeight,
        double seenPenalty,
        double heavySeenPenalty
) {
}
