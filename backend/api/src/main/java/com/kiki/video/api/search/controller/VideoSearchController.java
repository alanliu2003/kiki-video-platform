package com.kiki.video.api.search.controller;

import com.kiki.video.api.openapi.OpenApiTags;
import com.kiki.video.api.search.dto.VideoSearchResponse;
import com.kiki.video.api.search.service.VideoSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/search")
@Tag(name = OpenApiTags.SEARCH)
public class VideoSearchController {

    private final VideoSearchService videoSearchService;

    public VideoSearchController(VideoSearchService videoSearchService) {
        this.videoSearchService = videoSearchService;
    }

    @GetMapping("/videos")
    @Operation(summary = "Search public videos", description = "q is required. Invalid sort/status is 400. Elasticsearch down is 503 SEARCH_UNAVAILABLE.")
    public VideoSearchResponse searchVideos(
            @RequestParam String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String processingStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAfter,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdBefore
    ) {
        return videoSearchService.search(q, page, size, sort, ownerId, processingStatus, createdAfter, createdBefore);
    }
}
