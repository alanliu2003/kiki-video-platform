package com.kiki.video.common.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HlsPlaylistRewriterTest {

    @Test
    void rewritesBareUriLinesAndLeavesTags() {
        String input = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=800000\n360p/index.m3u8\n";
        String rewritten = HlsPlaylistRewriter.rewrite(input, uri -> "https://cdn.example/" + uri);
        assertThat(rewritten).contains("#EXT-X-STREAM-INF:BANDWIDTH=800000");
        assertThat(rewritten).contains("https://cdn.example/360p/index.m3u8");
        assertThat(rewritten).doesNotContain("\n360p/index.m3u8\n");
    }

    @Test
    void rewritesQuotedUriAttributes() {
        String input = "#EXT-X-MAP:URI=\"init.mp4\"";
        String rewritten = HlsPlaylistRewriter.rewrite(input, uri -> "https://cdn.example/" + uri);
        assertThat(rewritten).isEqualTo("#EXT-X-MAP:URI=\"https://cdn.example/init.mp4\"");
    }

    @Test
    void resolveChildJoinsPlaylistDirectory() {
        assertThat(HlsPlaylistRewriter.resolveChild("360p/index.m3u8", "segment000.ts"))
                .contains("360p/segment000.ts");
        assertThat(HlsPlaylistRewriter.resolveChild("master.m3u8", "360p/index.m3u8"))
                .contains("360p/index.m3u8");
    }

    @Test
    void resolveChildRejectsTraversalAndAbsoluteUris() {
        assertThat(HlsPlaylistRewriter.resolveChild("360p/index.m3u8", "../raw/secret")).isEmpty();
        assertThat(HlsPlaylistRewriter.resolveChild("360p/index.m3u8", "https://evil.example/x")).isEmpty();
        assertThat(HlsPlaylistRewriter.resolveChild("360p/index.m3u8", "/absolute")).isEmpty();
    }
}
