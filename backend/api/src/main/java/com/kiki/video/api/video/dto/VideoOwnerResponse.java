package com.kiki.video.api.video.dto;

import com.kiki.video.api.user.model.User;

public record VideoOwnerResponse(Long id, String username, String displayName) {

    public static VideoOwnerResponse from(User user) {
        return new VideoOwnerResponse(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
