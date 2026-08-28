package com.kiki.video.common.media;

import java.util.Optional;
import java.util.regex.Pattern;

public final class HlsAssetPaths {

    private static final Pattern SAFE_RELATIVE = Pattern.compile(
            "^(master\\.m3u8|thumbnail\\.jpg|\\d{3,4}p/(index\\.m3u8|segment\\d{3,}\\.ts))$"
    );

    private HlsAssetPaths() {
    }

    public static Optional<String> resolve(long mediaObjectId, String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            return Optional.empty();
        }
        String relative = requestedPath.replace('\\', '/');
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        if (relative.contains("..") || relative.contains("//") || relative.startsWith("processed/")) {
            return Optional.empty();
        }
        if (!SAFE_RELATIVE.matcher(relative).matches()) {
            return Optional.empty();
        }
        String objectKey = ProcessedObjectKeys.prefix(mediaObjectId) + relative;
        String prefix = ProcessedObjectKeys.prefix(mediaObjectId);
        if (!objectKey.startsWith(prefix)) {
            return Optional.empty();
        }
        return Optional.of(objectKey);
    }

    public static String contentType(String objectKey) {
        if (objectKey.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }
        if (objectKey.endsWith(".ts")) {
            return "video/mp2t";
        }
        if (objectKey.endsWith(".jpg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }
}
