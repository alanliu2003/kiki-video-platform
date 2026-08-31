package com.kiki.video.api.video.controller;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.video.dto.PlaybackResponse;
import com.kiki.video.api.video.dto.VideoListResponse;
import com.kiki.video.api.video.dto.VideoResponse;
import com.kiki.video.api.video.dto.VideoUploadResponse;
import com.kiki.video.api.video.http.HttpByteRange;
import com.kiki.video.api.video.model.Video;
import com.kiki.video.api.video.service.PlaybackService;
import com.kiki.video.api.video.service.VideoService;
import com.kiki.video.api.video.storage.StoredVideoObject;
import com.kiki.video.common.ApiConstants;
import com.kiki.video.common.media.HlsAssetPaths;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

@RestController
@RequestMapping(ApiConstants.API_PREFIX)
public class VideoController {

    private final VideoService videoService;
    private final PlaybackService playbackService;

    public VideoController(VideoService videoService, PlaybackService playbackService) {
        this.videoService = videoService;
        this.playbackService = playbackService;
    }

    @PostMapping(path = "/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoUploadResponse> upload(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam("title") String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file
    ) {
        VideoUploadResponse response = videoService.upload(principal.userId(), title, description, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/videos/{videoId:\\d+}")
    public VideoResponse getVideo(@PathVariable Long videoId) {
        return videoService.getVideo(videoId);
    }

    @GetMapping("/videos/{videoId}/playback")
    public PlaybackResponse playback(@PathVariable Long videoId) {
        return playbackService.playback(videoId);
    }

    @GetMapping("/videos/{videoId}/hls/{*assetPath}")
    public ResponseEntity<StreamingResponseBody> hls(
            @PathVariable Long videoId,
            @PathVariable("assetPath") String assetPath
    ) {
        String objectKey = playbackService.resolveHlsObjectKey(videoId, assetPath);
        return streamObject(objectKey, cacheControlFor(objectKey));
    }

    @GetMapping("/videos/{videoId}/thumbnail")
    public ResponseEntity<StreamingResponseBody> thumbnail(@PathVariable Long videoId) {
        String objectKey = playbackService.resolveThumbnailKey(videoId);
        return streamObject(objectKey, "private, max-age=3600");
    }

    @GetMapping("/users/me/videos")
    public VideoListResponse listMine(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return videoService.listMine(principal.userId(), page, size);
    }

    @GetMapping("/videos/{videoId}/content")
    public ResponseEntity<StreamingResponseBody> content(
            @PathVariable Long videoId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) {
        Video video = videoService.requireVideo(videoId);
        long totalSize = video.getFileSizeBytes();
        HttpByteRange range = HttpByteRange.parse(rangeHeader, totalSize)
                .orElse(new HttpByteRange(0, totalSize - 1));
        boolean partial = rangeHeader != null && !rangeHeader.isBlank();

        StoredVideoObject object = videoService.openContent(video, range.start(), range.length());
        InputStream stream = object.stream();
        StreamingResponseBody body = output -> {
            try (stream) {
                stream.transferTo(output);
            }
        };

        HttpStatus status = partial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(video.getContentType()))
                .contentLength(range.length())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0");
        if (partial) {
            builder.header(HttpHeaders.CONTENT_RANGE, range.contentRange(totalSize));
        }
        return builder.body(body);
    }

    private ResponseEntity<StreamingResponseBody> streamObject(String objectKey, String cacheControl) {
        long size = videoService.objectSize(objectKey);
        StoredVideoObject object = videoService.openObject(objectKey, 0, size);
        InputStream stream = object.stream();
        StreamingResponseBody body = output -> {
            try (stream) {
                stream.transferTo(output);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(HlsAssetPaths.contentType(objectKey)))
                .contentLength(size)
                .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                .body(body);
    }

    private static String cacheControlFor(String objectKey) {
        if (objectKey.endsWith(".ts")) {
            return "private, max-age=86400";
        }
        if (objectKey.endsWith(".jpg")) {
            return "private, max-age=3600";
        }
        return "private, max-age=10";
    }
}
