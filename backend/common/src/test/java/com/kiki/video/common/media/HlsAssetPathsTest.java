package com.kiki.video.common.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HlsAssetPathsTest {

    @Test
    void resolvesSafeMasterAndVariantPaths() {
        assertThat(HlsAssetPaths.resolve(42, "master.m3u8"))
                .contains("processed/42/master.m3u8");
        assertThat(HlsAssetPaths.resolve(42, "/720p/index.m3u8"))
                .contains("processed/42/720p/index.m3u8");
        assertThat(HlsAssetPaths.resolve(42, "360p/segment000.ts"))
                .contains("processed/42/360p/segment000.ts");
        assertThat(HlsAssetPaths.resolve(42, "thumbnail.jpg"))
                .contains("processed/42/thumbnail.jpg");
    }

    @Test
    void rejectsPathTraversalAndUnknownFiles() {
        assertThat(HlsAssetPaths.resolve(42, "../raw/secret")).isEmpty();
        assertThat(HlsAssetPaths.resolve(42, "360p/../master.m3u8")).isEmpty();
        assertThat(HlsAssetPaths.resolve(42, "processed/42/master.m3u8")).isEmpty();
        assertThat(HlsAssetPaths.resolve(42, "360p/notes.txt")).isEmpty();
        assertThat(HlsAssetPaths.resolve(42, "360p/segment.ts")).isEmpty();
    }

    @Test
    void mapsContentTypes() {
        assertThat(HlsAssetPaths.contentType("processed/1/master.m3u8"))
                .isEqualTo("application/vnd.apple.mpegurl");
        assertThat(HlsAssetPaths.contentType("processed/1/360p/segment000.ts"))
                .isEqualTo("video/mp2t");
        assertThat(HlsAssetPaths.contentType("processed/1/thumbnail.jpg"))
                .isEqualTo("image/jpeg");
    }
}
