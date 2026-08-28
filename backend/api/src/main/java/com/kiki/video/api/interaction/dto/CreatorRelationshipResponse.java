package com.kiki.video.api.interaction.dto;

public record CreatorRelationshipResponse(
        long followerCount,
        boolean followedByCurrentUser
) {
}
