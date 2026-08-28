package com.kiki.video.api.interaction.controller;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.interaction.dto.CreatorRelationshipResponse;
import com.kiki.video.api.interaction.service.FollowService;
import com.kiki.video.common.ApiConstants;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_PREFIX + "/users/{userId}")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @GetMapping("/relationship")
    public CreatorRelationshipResponse relationship(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return followService.relationship(userId, principal);
    }

    @PutMapping("/follow")
    public CreatorRelationshipResponse follow(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return followService.follow(userId, principal);
    }

    @DeleteMapping("/follow")
    public CreatorRelationshipResponse unfollow(
            @PathVariable Long userId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return followService.unfollow(userId, principal);
    }
}
