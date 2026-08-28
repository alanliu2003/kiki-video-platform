package com.kiki.video.api.upload;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadMathTest {

    @Test
    void totalChunksRoundsUpAndAllowsASmallerFinalChunk() {
        assertThat(UploadMath.totalChunks(8_388_608, 8_388_608)).isEqualTo(1);
        assertThat(UploadMath.totalChunks(8_388_609, 8_388_608)).isEqualTo(2);
        assertThat(UploadMath.expectedChunkSize(600_000, 256_000, 0)).isEqualTo(256_000);
        assertThat(UploadMath.expectedChunkSize(600_000, 256_000, 1)).isEqualTo(256_000);
        assertThat(UploadMath.expectedChunkSize(600_000, 256_000, 2)).isEqualTo(88_000);
    }

    @Test
    void rejectsInvalidChunkIndexesAndSizes() {
        assertThatThrownBy(() -> UploadMath.expectedChunkSize(100, 50, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UploadMath.totalChunks(0, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesSha256Hex() {
        assertThat(UploadMath.isSha256("A".repeat(64))).isTrue();
        assertThat(UploadMath.isSha256("xyz")).isFalse();
        assertThat(UploadMath.normalizeSha256("AB" + "cd".repeat(31))).isEqualTo("ab" + "cd".repeat(31));
    }
}
