package com.kiki.video.api.interaction.controller;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.interaction.dto.VideoInteractionResponse;
import com.kiki.video.api.interaction.service.VideoInteractionService;
import com.kiki.video.api.openapi.OpenApiTags;
import com.kiki.video.common.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_PREFIX + "/videos/{videoId}")
@Tag(name = OpenApiTags.SOCIAL)
public class VideoInteractionController {

    private final VideoInteractionService videoInteractionService;

    public VideoInteractionController(VideoInteractionService videoInteractionService) {
        this.videoInteractionService = videoInteractionService;
    }

    @GetMapping("/interactions")
    @Operation(summary = "Like, favorite, and comment counts")
    public VideoInteractionResponse interactions(
            @PathVariable Long videoId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return videoInteractionService.summary(videoId, principal);
    }

    @PutMapping("/like")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Like a video")
    public VideoInteractionResponse like(
            @PathVariable Long videoId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return videoInteractionService.like(videoId, principal);
    }

    @DeleteMapping("/like")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Unlike a video")
    public VideoInteractionResponse unlike(
            @PathVariable Long videoId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return videoInteractionService.unlike(videoId, principal);
    }

    @PutMapping("/favorite")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Favorite a video")
    public VideoInteractionResponse favorite(
            @PathVariable Long videoId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return videoInteractionService.favorite(videoId, principal);
    }

    @DeleteMapping("/favorite")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Unfavorite a video")
    public VideoInteractionResponse unfavorite(
            @PathVariable Long videoId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return videoInteractionService.unfavorite(videoId, principal);
    }
}
