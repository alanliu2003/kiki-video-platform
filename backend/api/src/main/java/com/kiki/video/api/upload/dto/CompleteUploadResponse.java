package com.kiki.video.api.upload.dto;

import com.kiki.video.api.video.dto.VideoResponse;

public record CompleteUploadResponse(
        VideoResponse video,
        boolean deduplicated
) {
}
