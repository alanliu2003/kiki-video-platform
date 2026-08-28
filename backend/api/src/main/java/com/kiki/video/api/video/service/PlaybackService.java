package com.kiki.video.api.video.service;

import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.upload.model.MediaObject;
import com.kiki.video.api.video.dto.PlaybackResponse;
import com.kiki.video.api.video.model.Video;
import com.kiki.video.common.media.HlsAssetPaths;
import com.kiki.video.common.media.MediaProcessingStatus;
import com.kiki.video.common.media.ProcessedObjectKeys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PlaybackService {

    private final VideoService videoService;

    public PlaybackService(VideoService videoService) {
        this.videoService = videoService;
    }

    public PlaybackResponse playback(Long videoId) {
        Video video = videoService.requireVideo(videoId);
        MediaObject media = videoService.requireMedia(video);
        MediaProcessingStatus status = media == null || media.getProcessingStatus() == null
                ? MediaProcessingStatus.NOT_REQUESTED
                : media.getProcessingStatus();

        if (status == MediaProcessingStatus.READY && media.getMasterPlaylistKey() != null) {
            return new PlaybackResponse(
                    status.name(),
                    "HLS",
                    "/api/videos/" + videoId + "/hls/master.m3u8",
                    "/api/videos/" + videoId + "/content",
                    media.getThumbnailKey() == null ? null : "/api/videos/" + videoId + "/thumbnail"
            );
        }
        if (status == MediaProcessingStatus.NOT_REQUESTED) {
            return new PlaybackResponse(
                    status.name(),
                    "ORIGINAL",
                    null,
                    "/api/videos/" + videoId + "/content",
                    null
            );
        }
        return new PlaybackResponse(status.name(), "NONE", null, null, null);
    }

    public String resolveHlsObjectKey(Long videoId, String requestedPath) {
        MediaObject media = requireReadyMedia(videoId);
        String objectKey = HlsAssetPaths.resolve(media.getId(), requestedPath)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.VALIDATION_ERROR,
                        HttpStatus.BAD_REQUEST,
                        "Invalid processed media path"
                ));
        String prefix = media.getProcessedPrefix() == null
                ? ProcessedObjectKeys.prefix(media.getId())
                : media.getProcessedPrefix();
        if (!objectKey.startsWith(prefix)) {
            throw new ApiException(ErrorCode.VIDEO_NOT_FOUND, HttpStatus.NOT_FOUND, "Processed media asset was not found");
        }
        if (!videoService.objectExists(objectKey)) {
            throw new ApiException(ErrorCode.VIDEO_NOT_FOUND, HttpStatus.NOT_FOUND, "Processed media asset was not found");
        }
        return objectKey;
    }

    public String resolveThumbnailKey(Long videoId) {
        MediaObject media = requireReadyMedia(videoId);
        if (media.getThumbnailKey() == null || media.getThumbnailKey().isBlank()) {
            throw new ApiException(ErrorCode.VIDEO_NOT_FOUND, HttpStatus.NOT_FOUND, "Thumbnail was not found");
        }
        return media.getThumbnailKey();
    }

    private MediaObject requireReadyMedia(Long videoId) {
        Video video = videoService.requireVideo(videoId);
        MediaObject media = videoService.requireMedia(video);
        if (media == null || media.getProcessingStatus() != MediaProcessingStatus.READY) {
            throw new ApiException(ErrorCode.VIDEO_NOT_FOUND, HttpStatus.NOT_FOUND, "Processed media is not ready");
        }
        return media;
    }
}
