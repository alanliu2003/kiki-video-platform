package com.kiki.video.api.upload.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InitUploadResponse(
        UUID uploadId,
        long chunkSizeBytes,
        int totalChunks,
        List<Integer> uploadedChunks,
        boolean deduplicated,
        boolean uploadRequired,
        Long mediaObjectId,
        String status,
        Instant expiresAt
) {
}
