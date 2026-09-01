package com.kiki.video.api.video.delivery;

import com.kiki.video.api.config.MediaDeliveryMode;
import com.kiki.video.api.config.MediaDeliveryProperties;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.video.storage.StoredVideoObject;
import com.kiki.video.api.video.storage.VideoStorage;
import com.kiki.video.api.video.storage.VideoStorageException;
import com.kiki.video.common.media.HlsAssetPaths;
import com.kiki.video.common.media.HlsPlaylistRewriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

@Service
public class MediaDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(MediaDeliveryService.class);
    private static final long MAX_PLAYLIST_BYTES = 512 * 1024;

    private final MediaDeliveryProperties properties;
    private final ObjectUrlSigner signer;
    private final VideoStorage videoStorage;

    public MediaDeliveryService(
            MediaDeliveryProperties properties,
            ObjectUrlSigner signer,
            VideoStorage videoStorage
    ) {
        this.properties = properties;
        this.signer = signer;
        this.videoStorage = videoStorage;
    }

    public MediaDeliveryMode mode() {
        return properties.deliveryMode();
    }

    public Duration urlTtl() {
        return properties.urlTtl();
    }

    public Instant expiresAt() {
        return Instant.now().plus(urlTtl());
    }

    public String cardThumbnailUrl(long videoId, boolean available) {
        return available ? MediaUrls.proxyThumbnail(videoId) : null;
    }

    public String playbackThumbnailUrl(long videoId, String thumbnailKey) {
        if (thumbnailKey == null || thumbnailKey.isBlank()) {
            return null;
        }
        if (!mode().isPresigned()) {
            return MediaUrls.proxyThumbnail(videoId);
        }
        return signTrustedKey(thumbnailKey);
    }

    public String masterPlaylistUrl(long videoId) {
        return MediaUrls.proxyMasterPlaylist(videoId);
    }

    public String legacyContentUrl(long videoId, String objectKey) {
        if (!mode().isPresigned()) {
            return MediaUrls.proxyContent(videoId);
        }
        return signTrustedKey(objectKey);
    }

    public String rewritePlaylistIfNeeded(long mediaObjectId, String requestedPath, String objectKey) {
        if (!mode().isPresigned() || objectKey == null || !objectKey.endsWith(".m3u8")) {
            return null;
        }
        String body = readSmallObject(objectKey);
        String playlistPath = requestedPath == null ? "" : requestedPath.replace('\\', '/');
        if (playlistPath.startsWith("/")) {
            playlistPath = playlistPath.substring(1);
        }
        String relativePlaylist = playlistPath;
        return HlsPlaylistRewriter.rewrite(body, child -> rewriteChild(mediaObjectId, relativePlaylist, child));
    }

    private String rewriteChild(long mediaObjectId, String playlistRelativePath, String childUri) {
        if (childUri == null || childUri.isBlank()) {
            return childUri;
        }
        String trimmed = childUri.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String relative = HlsPlaylistRewriter.resolveChild(playlistRelativePath, trimmed).orElse(null);
        if (relative == null) {
            return trimmed;
        }
        String objectKey = HlsAssetPaths.resolve(mediaObjectId, relative).orElse(null);
        if (objectKey == null) {
            return trimmed;
        }
        if (objectKey.endsWith(".m3u8")) {
            return trimmed;
        }
        return signTrustedKey(objectKey);
    }

    private String signTrustedKey(String objectKey) {
        try {
            return signer.presignGet(objectKey, urlTtl());
        } catch (VideoStorageException ex) {
            throw new ApiException(
                    ErrorCode.VIDEO_STORAGE_ERROR,
                    HttpStatus.BAD_GATEWAY,
                    "Video storage is unavailable"
            );
        }
    }

    private String readSmallObject(String objectKey) {
        long size = videoStorage.size(objectKey);
        if (size > MAX_PLAYLIST_BYTES) {
            throw new ApiException(
                    ErrorCode.VALIDATION_ERROR,
                    HttpStatus.BAD_REQUEST,
                    "HLS playlist is too large to rewrite"
            );
        }
        try (StoredVideoObject object = videoStorage.open(objectKey, 0, size)) {
            byte[] bytes = object.stream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.warn("Unable to read HLS playlist objectKey={}", objectKey);
            throw new ApiException(
                    ErrorCode.VIDEO_STORAGE_ERROR,
                    HttpStatus.BAD_GATEWAY,
                    "Video storage is unavailable"
            );
        }
    }
}
