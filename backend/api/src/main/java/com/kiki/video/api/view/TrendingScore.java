package com.kiki.video.api.view;

public final class TrendingScore {

    public record Weights(
            double views,
            double likes,
            double favorites,
            double comments,
            double ageDecayPerHour
    ) {
    }

    private TrendingScore() {
    }

    public static double score(
            long views,
            long likes,
            long favorites,
            long comments,
            double ageHours,
            Weights weights
    ) {
        double age = Math.max(0, ageHours);
        return Math.log1p(Math.max(0, views)) * weights.views()
                + Math.log1p(Math.max(0, likes)) * weights.likes()
                + Math.log1p(Math.max(0, favorites)) * weights.favorites()
                + Math.log1p(Math.max(0, comments)) * weights.comments()
                - age * weights.ageDecayPerHour();
    }
}
