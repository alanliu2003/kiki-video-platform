package com.kiki.video.api.video.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpByteRangeTest {

    @Test
    void parsesClosedRange() {
        HttpByteRange range = HttpByteRange.parse("bytes=0-1023", 4096).orElseThrow();
        assertThat(range.start()).isZero();
        assertThat(range.endInclusive()).isEqualTo(1023);
        assertThat(range.length()).isEqualTo(1024);
        assertThat(range.contentRange(4096)).isEqualTo("bytes 0-1023/4096");
    }

    @Test
    void parsesOpenEndedRange() {
        HttpByteRange range = HttpByteRange.parse("bytes=100-", 250).orElseThrow();
        assertThat(range.start()).isEqualTo(100);
        assertThat(range.endInclusive()).isEqualTo(249);
        assertThat(range.length()).isEqualTo(150);
    }

    @Test
    void parsesSuffixRange() {
        HttpByteRange range = HttpByteRange.parse("bytes=-50", 200).orElseThrow();
        assertThat(range.start()).isEqualTo(150);
        assertThat(range.endInclusive()).isEqualTo(199);
    }

    @Test
    void emptyHeaderMeansFullContent() {
        assertThat(HttpByteRange.parse(null, 100)).isEmpty();
        assertThat(HttpByteRange.parse(" ", 100)).isEmpty();
    }

    @Test
    void rejectsMalformedRanges() {
        assertThatThrownBy(() -> HttpByteRange.parse("items=0-10", 100))
                .isInstanceOf(HttpByteRange.MalformedRangeException.class);
        assertThatThrownBy(() -> HttpByteRange.parse("bytes=abc-10", 100))
                .isInstanceOf(HttpByteRange.MalformedRangeException.class);
        assertThatThrownBy(() -> HttpByteRange.parse("bytes=50-10", 100))
                .isInstanceOf(HttpByteRange.MalformedRangeException.class);
        assertThatThrownBy(() -> HttpByteRange.parse("bytes=0-10,20-30", 100))
                .isInstanceOf(HttpByteRange.MalformedRangeException.class);
    }

    @Test
    void rejectsUnsatisfiableRanges() {
        assertThatThrownBy(() -> HttpByteRange.parse("bytes=200-300", 100))
                .isInstanceOf(HttpByteRange.UnsatisfiableRangeException.class);
        assertThatThrownBy(() -> HttpByteRange.parse("bytes=0-10", 0))
                .isInstanceOf(HttpByteRange.UnsatisfiableRangeException.class);
    }
}
