package com.kiki.video.api.upload.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UploadStatusResponse(
        UUID uploadId,
        String status,
        int totalChunks,
        List<Integer> uploadedChunks,
        List<Integer> missingChunks,
        Instant expiresAt,
        boolean deduplicated,
        boolean uploadRequired
) {
}
