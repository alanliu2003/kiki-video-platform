package com.kiki.video.api.view.controller;

import com.kiki.video.api.view.dto.VideoFeedResponse;
import com.kiki.video.api.view.service.VideoDiscoveryService;
import com.kiki.video.common.ApiConstants;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.API_PREFIX)
public class VideoDiscoveryController {

    private final VideoDiscoveryService videoDiscoveryService;

    public VideoDiscoveryController(VideoDiscoveryService videoDiscoveryService) {
        this.videoDiscoveryService = videoDiscoveryService;
    }

    @GetMapping("/videos/trending")
    public VideoFeedResponse trending(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return videoDiscoveryService.trending(page, size);
    }

    @GetMapping("/videos/recent")
    public VideoFeedResponse recent(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return videoDiscoveryService.recent(page, size);
    }
}
