package com.kiki.video.api.interaction.dto;

public record VideoInteractionResponse(
        long likeCount,
        long favoriteCount,
        long commentCount,
        boolean likedByCurrentUser,
        boolean favoritedByCurrentUser
) {
}
