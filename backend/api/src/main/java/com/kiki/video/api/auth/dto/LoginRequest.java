package com.kiki.video.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "is required")
        String identifier,

        @NotBlank(message = "is required")
        String password
) {

    public LoginRequest {
        if (identifier != null) {
            identifier = identifier.trim();
        }
    }
}
