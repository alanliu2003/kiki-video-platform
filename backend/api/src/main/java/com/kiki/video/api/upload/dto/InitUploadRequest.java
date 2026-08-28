package com.kiki.video.api.upload.dto;

public record InitUploadRequest(
        String fileName,
        Long fileSizeBytes,
        String contentType,
        String fileSha256
) {
}
