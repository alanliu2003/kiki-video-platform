package com.kiki.video.api.recommendation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RecommendationScoreTest {

    private static final RecommendationScore.Weights WEIGHTS = new RecommendationScore.Weights(
            4.0, 3.0, 1.5, 1.2, 1.5, 0.8, 48.0, 0.05, 4.0, 10.0
    );

    @Test
    void usesLog1pForAffinityAndEngagement() {
        double none = RecommendationScore.score(signals(0, false, 0, 0, 0, 0, 0, 0), WEIGHTS);
        double liked = RecommendationScore.score(signals(2, false, 0, 0, 0, 0, 0, 0), WEIGHTS);
        double popular = RecommendationScore.score(signals(0, false, 10_000, 0, 0, 0, 0, 0), WEIGHTS);

        assertThat(liked).isGreaterThan(none);
        assertThat(liked - none).isCloseTo(Math.log1p(2) * 4.0, within(1e-9));
        assertThat(popular - none).isCloseTo(Math.log1p(10_000) * 1.5, within(1e-9));
        assertThat(popular).isLessThan(none + 100);
    }

    @Test
    void followedCreatorAddsExactBoost() {
        double plain = RecommendationScore.score(signals(0, false, 0, 0, 0, 0, 48, 0), WEIGHTS);
        double followed = RecommendationScore.score(signals(0, true, 0, 0, 0, 0, 48, 0), WEIGHTS);
        assertThat(followed - plain).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void freshnessBoostDecaysToZeroAfterWindow() {
        double fresh = RecommendationScore.freshnessBoost(0, WEIGHTS);
        double mid = RecommendationScore.freshnessBoost(24, WEIGHTS);
        double old = RecommendationScore.freshnessBoost(48, WEIGHTS);
        double older = RecommendationScore.freshnessBoost(100, WEIGHTS);
        assertThat(fresh).isCloseTo(48 * 0.05, within(1e-9));
        assertThat(mid).isCloseTo(24 * 0.05, within(1e-9));
        assertThat(old).isZero();
        assertThat(older).isZero();
    }

    @Test
    void seenPenaltyIsZeroThenSingleThenHeavy() {
        assertThat(RecommendationScore.alreadySeenPenalty(0, WEIGHTS)).isZero();
        assertThat(RecommendationScore.alreadySeenPenalty(1, WEIGHTS)).isEqualTo(4.0);
        assertThat(RecommendationScore.alreadySeenPenalty(2, WEIGHTS)).isEqualTo(10.0);
        assertThat(RecommendationScore.alreadySeenPenalty(5, WEIGHTS)).isEqualTo(10.0);
    }

    @Test
    void secondarySignalsAreAdditiveAndDeterministic() {
        RecommendationScore.Signals base = signals(0, false, 10, 0, 0, 0, 0, 0);
        double viewsOnly = RecommendationScore.score(base, WEIGHTS);
        double withLikes = RecommendationScore.score(signals(0, false, 10, 5, 0, 0, 0, 0), WEIGHTS);
        double withFavs = RecommendationScore.score(signals(0, false, 10, 0, 5, 0, 0, 0), WEIGHTS);
        double withComments = RecommendationScore.score(signals(0, false, 10, 0, 0, 5, 0, 0), WEIGHTS);
        double seen = RecommendationScore.score(signals(0, false, 10, 0, 0, 0, 0, 1), WEIGHTS);

        assertThat(withLikes).isGreaterThan(viewsOnly);
        assertThat(withFavs).isGreaterThan(withLikes);
        assertThat(withComments).isGreaterThan(viewsOnly);
        assertThat(seen).isEqualTo(viewsOnly - 4.0);
        assertThat(RecommendationScore.score(base, WEIGHTS)).isEqualTo(viewsOnly);
    }

    @Test
    void treatsNegativeAgeAsZero() {
        assertThat(RecommendationScore.freshnessBoost(-12, WEIGHTS))
                .isEqualTo(RecommendationScore.freshnessBoost(0, WEIGHTS));
    }

    private static RecommendationScore.Signals signals(
            double affinity,
            boolean followed,
            long views,
            long likes,
            long favorites,
            long comments,
            double ageHours,
            int seen
    ) {
        return new RecommendationScore.Signals(affinity, followed, views, likes, favorites, comments, ageHours, seen);
    }
}
