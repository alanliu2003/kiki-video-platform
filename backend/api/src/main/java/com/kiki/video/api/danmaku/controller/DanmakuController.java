package com.kiki.video.api.danmaku.controller;

import com.kiki.video.api.danmaku.dto.DanmakuResponse;
import com.kiki.video.api.danmaku.service.DanmakuService;
import com.kiki.video.api.openapi.OpenApiTags;
import com.kiki.video.common.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(ApiConstants.API_PREFIX + "/videos/{videoId}/danmaku")
@Tag(name = OpenApiTags.DANMAKU)
public class DanmakuController {

    private final DanmakuService danmakuService;

    public DanmakuController(DanmakuService danmakuService) {
        this.danmakuService = danmakuService;
    }

    @GetMapping
    @Operation(summary = "Historical danmaku for a playback window")
    public List<DanmakuResponse> list(
            @PathVariable Long videoId,
            @RequestParam(value = "fromMs", required = false) Long fromMs,
            @RequestParam(value = "toMs", required = false) Long toMs
    ) {
        return danmakuService.list(videoId, fromMs, toMs);
    }
}
