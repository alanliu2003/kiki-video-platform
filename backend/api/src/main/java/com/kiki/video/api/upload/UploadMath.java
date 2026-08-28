package com.kiki.video.api.upload;

import java.util.Locale;
import java.util.regex.Pattern;

public final class UploadMath {

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");

    private UploadMath() {
    }

    public static int totalChunks(long fileSizeBytes, long chunkSizeBytes) {
        if (fileSizeBytes <= 0 || chunkSizeBytes <= 0) {
            throw new IllegalArgumentException("File size and chunk size must be positive");
        }
        return (int) ((fileSizeBytes + chunkSizeBytes - 1) / chunkSizeBytes);
    }

    public static long expectedChunkSize(long fileSizeBytes, long chunkSizeBytes, int chunkIndex) {
        int total = totalChunks(fileSizeBytes, chunkSizeBytes);
        if (chunkIndex < 0 || chunkIndex >= total) {
            throw new IllegalArgumentException("Chunk index is out of range");
        }
        long start = chunkIndex * chunkSizeBytes;
        return Math.min(chunkSizeBytes, fileSizeBytes - start);
    }

    public static boolean isSha256(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    public static String normalizeSha256(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
