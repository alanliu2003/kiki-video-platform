package com.kiki.video.common.media;

import java.time.Instant;

public record MediaProcessingRequestedEvent(
        int eventVersion,
        long mediaObjectId,
        String sha256,
        String objectKey,
        Instant requestedAt
) {

    public static final int CURRENT_VERSION = 1;
    public static final String EVENT_TYPE = "MEDIA_PROCESSING_REQUESTED";

    public static MediaProcessingRequestedEvent create(long mediaObjectId, String sha256, String objectKey) {
        return new MediaProcessingRequestedEvent(
                CURRENT_VERSION,
                mediaObjectId,
                sha256,
                objectKey,
                Instant.now()
        );
    }
}
