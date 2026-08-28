package com.kiki.video.api.danmaku.service;

import com.kiki.video.api.config.DanmakuProperties;
import com.kiki.video.api.danmaku.dto.DanmakuResponse;
import com.kiki.video.api.danmaku.dto.DanmakuSubmitResult;
import com.kiki.video.api.danmaku.mapper.DanmakuMapper;
import com.kiki.video.api.danmaku.model.Danmaku;
import com.kiki.video.api.danmaku.model.DanmakuStatus;
import com.kiki.video.api.danmaku.model.DanmakuStyle;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.interaction.cache.InteractionRedisClient;
import com.kiki.video.api.interaction.cache.RedisKeys;
import com.kiki.video.api.upload.mapper.MediaObjectMapper;
import com.kiki.video.api.upload.model.MediaObject;
import com.kiki.video.api.user.mapper.UserMapper;
import com.kiki.video.api.user.model.User;
import com.kiki.video.api.video.mapper.VideoMapper;
import com.kiki.video.api.video.model.Video;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class DanmakuService {

    private final VideoMapper videoMapper;
    private final UserMapper userMapper;
    private final MediaObjectMapper mediaObjectMapper;
    private final DanmakuMapper danmakuMapper;
    private final InteractionRedisClient redis;
    private final DanmakuProperties properties;

    public DanmakuService(
            VideoMapper videoMapper,
            UserMapper userMapper,
            MediaObjectMapper mediaObjectMapper,
            DanmakuMapper danmakuMapper,
            InteractionRedisClient redis,
            DanmakuProperties properties
    ) {
        this.videoMapper = videoMapper;
        this.userMapper = userMapper;
        this.mediaObjectMapper = mediaObjectMapper;
        this.danmakuMapper = danmakuMapper;
        this.redis = redis;
        this.properties = properties;
    }

    public List<DanmakuResponse> list(Long videoId, Long fromMs, Long toMs) {
        requireVideo(videoId);
        long windowMs = properties.historyWindow().toMillis();
        long start = fromMs == null ? 0L : fromMs;
        long end = toMs == null ? start + windowMs : toMs;
        if (start < 0) {
            throw new ApiException(ErrorCode.INVALID_DANMAKU_WINDOW, HttpStatus.BAD_REQUEST, "fromMs must be >= 0");
        }
        if (end <= start) {
            throw new ApiException(ErrorCode.INVALID_DANMAKU_WINDOW, HttpStatus.BAD_REQUEST, "toMs must be greater than fromMs");
        }
        if (end - start > windowMs) {
            throw new ApiException(
                    ErrorCode.INVALID_DANMAKU_WINDOW,
                    HttpStatus.BAD_REQUEST,
                    "Time window must be at most " + windowMs + " milliseconds"
            );
        }
        return danmakuMapper.findActiveInWindow(videoId, start, end).stream()
                .map(DanmakuResponse::from)
                .toList();
    }

    public boolean videoExists(Long videoId) {
        return videoId != null && videoMapper.findById(videoId) != null;
    }

    @Transactional
    public DanmakuSubmitResult submit(Long videoId, Long userId, String clientMessageId, String content, Long videoTimeMs) {
        Video video = requireVideo(videoId);
        User user = requireUser(userId);
        String trimmedContent = normalizeContent(content);
        String messageId = normalizeClientMessageId(clientMessageId);
        long timeMs = requireVideoTimeMs(videoTimeMs);
        validateTimestamp(video, timeMs);
        enforceRateLimit(userId);

        Danmaku existing = danmakuMapper.findByUserAndClientMessageId(userId, messageId);
        if (existing != null) {
            return new DanmakuSubmitResult(DanmakuResponse.from(existing), false);
        }

        Instant now = Instant.now();
        Danmaku danmaku = new Danmaku();
        danmaku.setVideoId(videoId);
        danmaku.setUserId(user.getId());
        danmaku.setContent(trimmedContent);
        danmaku.setVideoTimeMs(timeMs);
        danmaku.setStyle(DanmakuStyle.NORMAL);
        danmaku.setStatus(DanmakuStatus.ACTIVE);
        danmaku.setClientMessageId(messageId);
        danmaku.setCreatedAt(now);
        danmaku.setUsername(user.getUsername());
        danmaku.setDisplayName(user.getDisplayName());
        int inserted = danmakuMapper.insert(danmaku);
        if (inserted == 0) {
            Danmaku replayed = danmakuMapper.findByUserAndClientMessageId(userId, messageId);
            if (replayed == null) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "Unable to persist danmaku");
            }
            return new DanmakuSubmitResult(DanmakuResponse.from(replayed), false);
        }
        return new DanmakuSubmitResult(DanmakuResponse.from(danmaku), true);
    }

    private void validateTimestamp(Video video, long videoTimeMs) {
        Double durationSeconds = null;
        if (video.getMediaObjectId() != null) {
            MediaObject media = mediaObjectMapper.findById(video.getMediaObjectId());
            if (media != null) {
                durationSeconds = media.getDurationSeconds();
            }
        }
        if (durationSeconds != null && durationSeconds > 0) {
            long maxMs = Math.round(durationSeconds * 1000.0) + properties.timestampTolerance().toMillis();
            if (videoTimeMs > maxMs) {
                throw new ApiException(
                        ErrorCode.INVALID_DANMAKU_TIMESTAMP,
                        HttpStatus.BAD_REQUEST,
                        "videoTimeMs is past the video duration"
                );
            }
            return;
        }
        if (videoTimeMs > properties.legacyMaxTimestamp().toMillis()) {
            throw new ApiException(
                    ErrorCode.INVALID_DANMAKU_TIMESTAMP,
                    HttpStatus.BAD_REQUEST,
                    "videoTimeMs exceeds the legacy timestamp limit"
            );
        }
    }

    private void enforceRateLimit(Long userId) {
        redis.incrementRate(RedisKeys.danmakuRateLimit(userId), properties.rateWindow())
                .ifPresent(count -> {
                    if (count > properties.rateLimit()) {
                        throw new ApiException(
                                ErrorCode.RATE_LIMITED,
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Too many danmaku; try again shortly"
                        );
                    }
                });
    }

    private Video requireVideo(Long videoId) {
        Video video = videoId == null ? null : videoMapper.findById(videoId);
        if (video == null) {
            throw new ApiException(ErrorCode.VIDEO_NOT_FOUND, HttpStatus.NOT_FOUND, "Video was not found");
        }
        return video;
    }

    private User requireUser(Long userId) {
        User user = userId == null ? null : userMapper.findById(userId);
        if (user == null) {
            throw new ApiException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User was not found");
        }
        return user;
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_DANMAKU, HttpStatus.BAD_REQUEST, "Content is required");
        }
        String trimmed = content.trim();
        if (trimmed.length() > properties.maxLength()) {
            throw new ApiException(
                    ErrorCode.INVALID_DANMAKU,
                    HttpStatus.BAD_REQUEST,
                    "Content must be at most " + properties.maxLength() + " characters"
            );
        }
        return trimmed;
    }

    private static String normalizeClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_DANMAKU, HttpStatus.BAD_REQUEST, "clientMessageId is required");
        }
        String trimmed = clientMessageId.trim();
        if (trimmed.length() > 64) {
            throw new ApiException(ErrorCode.INVALID_DANMAKU, HttpStatus.BAD_REQUEST, "clientMessageId is too long");
        }
        return trimmed;
    }

    private static long requireVideoTimeMs(Long videoTimeMs) {
        if (videoTimeMs == null || videoTimeMs < 0) {
            throw new ApiException(
                    ErrorCode.INVALID_DANMAKU_TIMESTAMP,
                    HttpStatus.BAD_REQUEST,
                    "videoTimeMs must be >= 0"
            );
        }
        return videoTimeMs;
    }
}
