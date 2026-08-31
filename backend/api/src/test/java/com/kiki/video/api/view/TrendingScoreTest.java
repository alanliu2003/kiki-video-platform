package com.kiki.video.api.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TrendingScoreTest {

    private static final TrendingScore.Weights WEIGHTS = new TrendingScore.Weights(3.0, 2.0, 2.0, 1.5, 0.02);

    @Test
    void usesLog1pSoLargeCountsDoNotDominateLinearly() {
        double ten = TrendingScore.score(10, 0, 0, 0, 0, WEIGHTS);
        double twenty = TrendingScore.score(20, 0, 0, 0, 0, WEIGHTS);
        double tenThousand = TrendingScore.score(10_000, 0, 0, 0, 0, WEIGHTS);

        assertThat(twenty - ten).isLessThan(ten);
        assertThat(tenThousand).isLessThan(TrendingScore.score(10, 0, 0, 0, 0, WEIGHTS) * 100);
        assertThat(tenThousand).isCloseTo(Math.log1p(10_000) * 3.0, within(1e-9));
    }

    @Test
    void appliesAgeDecayAndSecondarySignals() {
        double fresh = TrendingScore.score(10, 0, 0, 0, 0, WEIGHTS);
        double weekOld = TrendingScore.score(10, 0, 0, 0, 168, WEIGHTS);
        double liked = TrendingScore.score(10, 5, 2, 3, 0, WEIGHTS);

        assertThat(weekOld).isLessThan(fresh);
        assertThat(weekOld).isCloseTo(fresh - (168 * 0.02), within(1e-9));
        assertThat(liked).isGreaterThan(fresh);
    }

    @Test
    void treatsNegativeAgeAsZero() {
        assertThat(TrendingScore.score(4, 0, 0, 0, -12, WEIGHTS))
                .isEqualTo(TrendingScore.score(4, 0, 0, 0, 0, WEIGHTS));
    }
}
