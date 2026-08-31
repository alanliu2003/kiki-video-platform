package com.kiki.video.api.recommendation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationReasonTest {

    @Test
    void prefersFollowThenAffinityThenSource() {
        assertThat(RecommendationReason.resolve(true, 9, 1, true))
                .isEqualTo(RecommendationReason.FOLLOWED);
        assertThat(RecommendationReason.resolve(false, 2, 12, true))
                .isEqualTo(RecommendationReason.ENGAGED_NEW);
        assertThat(RecommendationReason.resolve(false, 2, 72, false))
                .isEqualTo(RecommendationReason.ENGAGED);
        assertThat(RecommendationReason.resolve(false, 0, 1, true))
                .isEqualTo(RecommendationReason.TRENDING);
        assertThat(RecommendationReason.resolve(false, 0, 1, false))
                .isEqualTo(RecommendationReason.RECENT);
    }
}
