package com.kiki.video.api.recommendation;

public final class RecommendationScore {

    public record Weights(
            double affinity,
            double followed,
            double views,
            double likes,
            double favorites,
            double comments,
            double freshnessHours,
            double freshnessWeight,
            double seenPenalty,
            double heavySeenPenalty
    ) {
    }

    public record Signals(
            double creatorAffinityPoints,
            boolean followedCreator,
            long viewCount,
            long likeCount,
            long favoriteCount,
            long commentCount,
            double ageHours,
            int qualifiedViewCount
    ) {
    }

    private RecommendationScore() {
    }

    public static double creatorAffinity(double affinityPoints) {
        return Math.log1p(Math.max(0, affinityPoints));
    }

    public static double alreadySeenPenalty(int qualifiedViewCount, Weights weights) {
        if (qualifiedViewCount <= 0) {
            return 0;
        }
        if (qualifiedViewCount >= 2) {
            return weights.heavySeenPenalty();
        }
        return weights.seenPenalty();
    }

    public static double freshnessBoost(double ageHours, Weights weights) {
        double age = Math.max(0, ageHours);
        double window = Math.max(0, weights.freshnessHours());
        return Math.max(0, window - age) * weights.freshnessWeight();
    }

    public static double score(Signals signals, Weights weights) {
        double followedBoost = signals.followedCreator() ? 1.0 : 0.0;
        return creatorAffinity(signals.creatorAffinityPoints()) * weights.affinity()
                + followedBoost * weights.followed()
                + Math.log1p(Math.max(0, signals.viewCount())) * weights.views()
                + Math.log1p(Math.max(0, signals.likeCount())) * weights.likes()
                + Math.log1p(Math.max(0, signals.favoriteCount())) * weights.favorites()
                + Math.log1p(Math.max(0, signals.commentCount())) * weights.comments()
                + freshnessBoost(signals.ageHours(), weights)
                - alreadySeenPenalty(signals.qualifiedViewCount(), weights);
    }
}
