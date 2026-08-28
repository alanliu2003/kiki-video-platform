package com.kiki.video.api.exception;

import java.time.Instant;

public record ApiErrorResponse(String code, String message, Instant timestamp) {

    public static ApiErrorResponse of(ErrorCode code, String message) {
        return new ApiErrorResponse(code.name(), message, Instant.now());
    }
}
