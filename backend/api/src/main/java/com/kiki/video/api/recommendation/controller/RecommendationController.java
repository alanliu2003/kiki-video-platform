package com.kiki.video.api.recommendation.controller;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.recommendation.dto.RecommendationFeedResponse;
import com.kiki.video.api.recommendation.service.RecommendationService;
import com.kiki.video.common.ApiConstants;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_PREFIX)
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/recommendations/videos")
    public RecommendationFeedResponse videos(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return recommendationService.recommend(principal.userId(), page, size);
    }
}
