package com.kiki.video.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "is required")
        @Size(min = 3, max = 30, message = "must be between 3 and 30 characters")
        @Pattern(regexp = "^[A-Za-z0-9_]{3,30}$", message = "may only contain letters, numbers, and underscores")
        String username,

        @NotBlank(message = "is required")
        @Email(message = "must be a valid email address")
        @Size(max = 255, message = "must be at most 255 characters")
        String email,

        @NotBlank(message = "is required")
        @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String password
) {

    public RegisterRequest {
        if (username != null) {
            username = username.trim();
        }
        if (email != null) {
            email = email.trim();
        }
    }
}
