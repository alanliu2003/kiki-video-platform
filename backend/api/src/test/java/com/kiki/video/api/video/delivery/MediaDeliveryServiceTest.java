package com.kiki.video.api.video.delivery;

import com.kiki.video.api.config.MediaDeliveryProperties;
import com.kiki.video.api.video.storage.StoredVideoObject;
import com.kiki.video.api.video.storage.VideoStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaDeliveryServiceTest {

    @Mock
    private ObjectUrlSigner signer;

    @Mock
    private VideoStorage videoStorage;

    @Test
    void proxyModeKeepsApiPathsAndDoesNotSign() {
        MediaDeliveryService service = service("proxy");
        assertThat(service.masterPlaylistUrl(9L)).isEqualTo("/api/videos/9/hls/master.m3u8");
        assertThat(service.legacyContentUrl(9L, "raw/abc")).isEqualTo("/api/videos/9/content");
        assertThat(service.playbackThumbnailUrl(9L, "processed/1/thumbnail.jpg"))
                .isEqualTo("/api/videos/9/thumbnail");
        assertThat(service.cardThumbnailUrl(9L, true)).isEqualTo("/api/videos/9/thumbnail");
        assertThat(service.rewritePlaylistIfNeeded(1L, "360p/index.m3u8", "processed/1/360p/index.m3u8"))
                .isNull();
        verify(signer, never()).presignGet(any(), any());
    }

    @Test
    void presignedModeSignsTrustedKeysAndRewritesSegmentsOnly() {
        when(signer.presignGet(eq("raw/abc"), any())).thenReturn("https://minio.example/raw/abc?sig=1");
        when(signer.presignGet(eq("processed/1/thumbnail.jpg"), any()))
                .thenReturn("https://minio.example/thumb?sig=1");
        when(signer.presignGet(eq("processed/1/360p/segment000.ts"), any()))
                .thenReturn("https://minio.example/segment000.ts?X-Amz-Signature=abc");
        byte[] playlist = "#EXTM3U\n#EXTINF:6,\nsegment000.ts\n".getBytes(StandardCharsets.UTF_8);
        when(videoStorage.size("processed/1/360p/index.m3u8")).thenReturn((long) playlist.length);
        when(videoStorage.open("processed/1/360p/index.m3u8", 0, playlist.length))
                .thenReturn(new StoredVideoObject(new ByteArrayInputStream(playlist), playlist.length));

        MediaDeliveryService service = service("presigned");
        assertThat(service.legacyContentUrl(9L, "raw/abc")).startsWith("https://minio.example/raw/abc");
        assertThat(service.playbackThumbnailUrl(9L, "processed/1/thumbnail.jpg"))
                .contains("minio.example/thumb");
        assertThat(service.masterPlaylistUrl(9L)).isEqualTo("/api/videos/9/hls/master.m3u8");
        String rewritten = service.rewritePlaylistIfNeeded(1L, "360p/index.m3u8", "processed/1/360p/index.m3u8");
        assertThat(rewritten).contains("X-Amz-Signature=abc");
        assertThat(rewritten).doesNotContain("\nsegment000.ts\n");
        assertThat(service.expiresAt()).isAfter(Instant.now());
        assertThat(service.urlTtl()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void doesNotSignArbitraryPlaylistChildren() {
        byte[] playlist = "#EXTM3U\n../raw/secret\n".getBytes(StandardCharsets.UTF_8);
        when(videoStorage.size("processed/1/360p/index.m3u8")).thenReturn((long) playlist.length);
        when(videoStorage.open("processed/1/360p/index.m3u8", 0, playlist.length))
                .thenReturn(new StoredVideoObject(new ByteArrayInputStream(playlist), playlist.length));

        MediaDeliveryService service = service("presigned");
        String rewritten = service.rewritePlaylistIfNeeded(1L, "360p/index.m3u8", "processed/1/360p/index.m3u8");
        assertThat(rewritten).contains("../raw/secret");
        verify(signer, never()).presignGet(any(), any());
    }

    private MediaDeliveryService service(String mode) {
        return new MediaDeliveryService(
                new MediaDeliveryProperties(mode, Duration.ofMinutes(15), java.util.List.of()),
                signer,
                videoStorage
        );
    }
}
