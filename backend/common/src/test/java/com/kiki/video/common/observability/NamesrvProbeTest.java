package com.kiki.video.common.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamesrvProbeTest {

    @Test
    void rejectsBlankOrMalformedAddresses() {
        assertThat(NamesrvProbe.reachable(null, 50)).isFalse();
        assertThat(NamesrvProbe.reachable(" ", 50)).isFalse();
        assertThat(NamesrvProbe.reachable("no-port", 50)).isFalse();
    }

    @Test
    void timesOutOnClosedPort() {
        assertThat(NamesrvProbe.reachable("127.0.0.1:1", 50)).isFalse();
    }
}
