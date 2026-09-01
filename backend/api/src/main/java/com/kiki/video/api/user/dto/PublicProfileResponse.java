package com.kiki.video.api.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Public creator profile. Never includes email, role, or auth fields.")
public record PublicProfileResponse(
        @Schema(example = "3") Long id,
        @Schema(example = "alice") String username,
        @Schema(example = "Alice") String displayName,
        Instant createdAt,
        @Schema(example = "12") long followerCount,
        @Schema(example = "4") long followingCount,
        @Schema(example = "6") long publicVideoCount,
        @Schema(example = "1840") long totalViews,
        @Schema(description = "Present only when the caller is authenticated") Boolean followedByCurrentUser
) {
}
