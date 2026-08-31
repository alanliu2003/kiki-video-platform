package com.kiki.video.api.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ViewQualificationTest {

    private static final double QUALIFY_SECONDS = 10;
    private static final double QUALIFY_PERCENT = 0.25;

    @Test
    void thresholdIsTenSecondsWhenDurationIsUnknown() {
        assertThat(ViewQualification.thresholdMs(null, QUALIFY_SECONDS, QUALIFY_PERCENT)).isEqualTo(10_000);
        assertThat(ViewQualification.thresholdMs(0L, QUALIFY_SECONDS, QUALIFY_PERCENT)).isEqualTo(10_000);
        assertThat(ViewQualification.thresholdMs(-5L, QUALIFY_SECONDS, QUALIFY_PERCENT)).isEqualTo(10_000);
    }

    @Test
    void thresholdUsesTwentyFivePercentWhenThatIsSmallerThanTenSeconds() {
        assertThat(ViewQualification.thresholdMs(20_000L, QUALIFY_SECONDS, QUALIFY_PERCENT)).isEqualTo(5_000);
        assertThat(ViewQualification.thresholdMs(8_000L, QUALIFY_SECONDS, QUALIFY_PERCENT)).isEqualTo(2_000);
    }

    @Test
    void thresholdCapsAtTenSecondsForLongVideos() {
        assertThat(ViewQualification.thresholdMs(60_000L, QUALIFY_SECONDS, QUALIFY_PERCENT)).isEqualTo(10_000);
        assertThat(ViewQualification.thresholdMs(3_600_000L, QUALIFY_SECONDS, QUALIFY_PERCENT)).isEqualTo(10_000);
    }

    @Test
    void shortVideosRemainCountableViaPercentOfDuration() {
        assertThat(ViewQualification.thresholdMs(4_000L, QUALIFY_SECONDS, QUALIFY_PERCENT)).isEqualTo(1_000);
        assertThat(ViewQualification.meets(1_000, 4_000L, QUALIFY_SECONDS, QUALIFY_PERCENT)).isTrue();
        assertThat(ViewQualification.meets(999, 4_000L, QUALIFY_SECONDS, QUALIFY_PERCENT)).isFalse();
    }

    @Test
    void prefersAuthoritativeDurationOverClientDuration() {
        Long resolved = ViewQualification.resolveDurationMs(60.0, 8_000L);
        assertThat(resolved).isEqualTo(60_000);
        assertThat(ViewQualification.meets(10_000, resolved, QUALIFY_SECONDS, QUALIFY_PERCENT)).isTrue();
        assertThat(ViewQualification.meets(9_999, resolved, QUALIFY_SECONDS, QUALIFY_PERCENT)).isFalse();
    }

    @Test
    void fallsBackToClientDurationWhenAuthoritativeDurationIsMissing() {
        assertThat(ViewQualification.resolveDurationMs(null, 8_000L)).isEqualTo(8_000);
        assertThat(ViewQualification.resolveDurationMs(0.0, 8_000L)).isEqualTo(8_000);
        assertThat(ViewQualification.resolveDurationMs(-1.0, 8_000L)).isEqualTo(8_000);
    }

    @Test
    void ignoresInvalidClientDurationAndWatchedValues() {
        assertThat(ViewQualification.resolveDurationMs(null, -1L)).isNull();
        assertThat(ViewQualification.resolveDurationMs(null, 0L)).isNull();
        assertThat(ViewQualification.isWatchedMsUsable(-1)).isFalse();
        assertThat(ViewQualification.isWatchedMsUsable(86_400_001)).isFalse();
        assertThat(ViewQualification.isWatchedMsUsable(0)).isTrue();
        assertThat(ViewQualification.meets(10_000, null, QUALIFY_SECONDS, QUALIFY_PERCENT)).isTrue();
        assertThat(ViewQualification.meets(9_999, null, QUALIFY_SECONDS, QUALIFY_PERCENT)).isFalse();
    }
}
