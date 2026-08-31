package com.kiki.video.api.recommendation;

public final class RecommendationReason {

    public static final String FOLLOWED = "Because you follow this creator";
    public static final String ENGAGED_NEW = "New from a creator you engage with";
    public static final String ENGAGED = "From a creator you engage with";
    public static final String TRENDING = "Trending now";
    public static final String RECENT = "New upload";

    private RecommendationReason() {
    }

    public static String resolve(
            boolean followed,
            double affinityPoints,
            double ageHours,
            boolean fromTrending
    ) {
        if (followed) {
            return FOLLOWED;
        }
        if (affinityPoints > 0) {
            return Math.max(0, ageHours) <= 48 ? ENGAGED_NEW : ENGAGED;
        }
        if (fromTrending) {
            return TRENDING;
        }
        return RECENT;
    }
}
