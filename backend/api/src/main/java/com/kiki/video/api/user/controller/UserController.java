package com.kiki.video.api.user.controller;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.openapi.OpenApiTags;
import com.kiki.video.api.user.dto.CurrentUserResponse;
import com.kiki.video.api.user.dto.PublicProfileResponse;
import com.kiki.video.api.user.service.UserService;
import com.kiki.video.api.view.dto.VideoFeedResponse;
import com.kiki.video.api.view.service.VideoDiscoveryService;
import com.kiki.video.common.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_PREFIX + "/users")
@Tag(name = OpenApiTags.USERS)
public class UserController {

    private final UserService userService;
    private final VideoDiscoveryService videoDiscoveryService;

    public UserController(UserService userService, VideoDiscoveryService videoDiscoveryService) {
        this.userService = userService;
        this.videoDiscoveryService = videoDiscoveryService;
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Current authenticated user", description = "Includes email and role. JWT required.")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return userService.getCurrentUser(principal.userId());
    }

    @GetMapping("/{userId:\\d+}")
    @Operation(
            summary = "Public creator profile",
            description = "Public fields only. Follow state is included only when a JWT is presented."
    )
    @ApiResponse(
            responseCode = "200",
            content = @Content(
                    schema = @Schema(implementation = PublicProfileResponse.class),
                    examples = @ExampleObject(
                            name = "anonymous",
                            value = """
                                    {
                                      "id": 3,
                                      "username": "alice",
                                      "displayName": "Alice",
                                      "createdAt": "2026-08-01T12:00:00Z",
                                      "followerCount": 12,
                                      "followingCount": 4,
                                      "publicVideoCount": 6,
                                      "totalViews": 1840
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(responseCode = "404", description = "User was not found")
    public PublicProfileResponse publicProfile(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return userService.getPublicProfile(userId, principal);
    }

    @GetMapping("/{userId:\\d+}/videos")
    @Operation(
            summary = "Creator's public videos",
            description = "Logical videos newest first. Page is zero-based. Size defaults to 20 and is clamped to 50."
    )
    public VideoFeedResponse publicVideos(
            @PathVariable Long userId,
            @Parameter(description = "Zero-based page") @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "Page size, default 20, max 50") @RequestParam(value = "size", required = false) Integer size
    ) {
        userService.requireActiveUser(userId);
        return videoDiscoveryService.listByOwner(userId, page, size);
    }
}
