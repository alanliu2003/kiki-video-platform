package com.kiki.video.api.user.dto;

import com.kiki.video.api.user.model.User;

import java.time.Instant;

public record CurrentUserResponse(
        Long id,
        String username,
        String email,
        String displayName,
        String role,
        Instant createdAt
) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getCreatedAt()
        );
    }
}
