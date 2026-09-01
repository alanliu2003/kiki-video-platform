package com.kiki.video.api.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdTest {

    @Test
    void acceptsUuidAndSafeTokens() {
        assertThat(RequestId.isValid("550e8400-e29b-41d4-a716-446655440000")).isTrue();
        assertThat(RequestId.isValid("load-test.view_1")).isTrue();
        assertThat(RequestId.resolve("550e8400-e29b-41d4-a716-446655440000"))
                .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void rejectsMissingInvalidAndOversizedValues() {
        assertThat(RequestId.isValid(null)).isFalse();
        assertThat(RequestId.isValid("")).isFalse();
        assertThat(RequestId.isValid("short")).isFalse();
        assertThat(RequestId.isValid("has space-in-id")).isFalse();
        assertThat(RequestId.isValid("Bearer " + "a".repeat(40))).isFalse();
        assertThat(RequestId.isValid("a".repeat(RequestId.MAX_LENGTH + 1))).isFalse();
        assertThat(RequestId.isValid("id/with/slash")).isFalse();
    }

    @Test
    void generatesWhenIncomingIsUntrusted() {
        String generated = RequestId.resolve("   ");
        assertThat(generated).hasSize(36);
        assertThat(RequestId.isValid(generated)).isTrue();
        assertThat(RequestId.resolve("a".repeat(500))).isNotEqualTo("a".repeat(500));
    }

    @Test
    void currentReadsMdc() {
        assertThat(RequestId.current()).isNull();
        MDC.put(RequestId.MDC_KEY, "550e8400-e29b-41d4-a716-446655440000");
        try {
            assertThat(RequestId.current()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        } finally {
            MDC.remove(RequestId.MDC_KEY);
        }
    }
}
