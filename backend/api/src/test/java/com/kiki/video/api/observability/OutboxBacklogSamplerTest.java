package com.kiki.video.api.observability;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxBacklogSamplerTest {

    @Test
    void ageSecondsIsZeroWhenEmptyOrFuture() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        assertThat(OutboxBacklogSampler.ageSeconds(null, now)).isZero();
        assertThat(OutboxBacklogSampler.ageSeconds(now.plusSeconds(10), now)).isZero();
        assertThat(OutboxBacklogSampler.ageSeconds(now.minusSeconds(90), now)).isEqualTo(90);
    }
}
