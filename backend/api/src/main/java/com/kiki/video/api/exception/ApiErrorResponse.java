package com.kiki.video.api.exception;

import com.kiki.video.api.observability.RequestId;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Structured API error. requestId matches X-Request-ID.")
public record ApiErrorResponse(
        @Schema(example = "VIDEO_NOT_FOUND") String code,
        @Schema(example = "Video was not found") String message,
        Instant timestamp,
        @Schema(example = "550e8400-e29b-41d4-a716-446655440000") String requestId
) {

    public static ApiErrorResponse of(ErrorCode code, String message) {
        return new ApiErrorResponse(code.name(), message, Instant.now(), RequestId.current());
    }
}
