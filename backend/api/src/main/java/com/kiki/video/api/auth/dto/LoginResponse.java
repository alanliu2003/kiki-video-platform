package com.kiki.video.api.auth.dto;

import com.kiki.video.api.user.dto.UserResponse;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
