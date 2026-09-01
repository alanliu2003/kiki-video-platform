package com.kiki.video.api.view.controller;

import com.kiki.video.api.openapi.OpenApiTags;
import com.kiki.video.api.view.dto.VideoFeedResponse;
import com.kiki.video.api.view.service.VideoDiscoveryService;
import com.kiki.video.common.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_PREFIX)
@Tag(name = OpenApiTags.DISCOVERY)
public class VideoDiscoveryController {

    private final VideoDiscoveryService videoDiscoveryService;

    public VideoDiscoveryController(VideoDiscoveryService videoDiscoveryService) {
        this.videoDiscoveryService = videoDiscoveryService;
    }

    @GetMapping("/videos/trending")
    @Operation(summary = "Trending public videos", description = "Zero-based page. Size defaults to 20 and is clamped to TRENDING_MAX_PAGE_SIZE.")
    public VideoFeedResponse trending(
            @Parameter(description = "Zero-based page") @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "Page size") @RequestParam(value = "size", required = false) Integer size
    ) {
        return videoDiscoveryService.trending(page, size);
    }

    @GetMapping("/videos/recent")
    @Operation(summary = "Newest public videos")
    public VideoFeedResponse recent(
            @Parameter(description = "Zero-based page") @RequestParam(value = "page", required = false) Integer page,
            @Parameter(description = "Page size") @RequestParam(value = "size", required = false) Integer size
    ) {
        return videoDiscoveryService.recent(page, size);
    }
}
