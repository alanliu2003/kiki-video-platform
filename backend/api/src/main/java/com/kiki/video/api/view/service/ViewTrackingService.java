package com.kiki.video.api.view.service;

import com.kiki.video.api.auth.security.AuthPrincipal;
import com.kiki.video.api.config.ViewTrackingProperties;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.interaction.cache.RedisKeys;
import com.kiki.video.api.recommendation.mapper.UserVideoQualifiedViewMapper;
import com.kiki.video.api.video.model.Video;
import com.kiki.video.api.video.service.VideoService;
import com.kiki.video.api.view.ViewQualification;
import com.kiki.video.api.view.ViewerIdentity;
import com.kiki.video.api.view.cache.ViewTrackingRedisClient;
import com.kiki.video.api.view.dto.QualifyViewRequest;
import com.kiki.video.api.view.dto.QualifyViewResponse;
import com.kiki.video.api.view.mapper.VideoViewMapper;
import com.kiki.video.api.view.model.VideoViewCount;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ViewTrackingService {

    private final VideoService videoService;
    private final VideoViewMapper videoViewMapper;
    private final UserVideoQualifiedViewMapper qualifiedViewMapper;
    private final ViewTrackingRedisClient redis;
    private final ViewTrackingProperties properties;

    public ViewTrackingService(
            VideoService videoService,
            VideoViewMapper videoViewMapper,
            UserVideoQualifiedViewMapper qualifiedViewMapper,
            ViewTrackingRedisClient redis,
            ViewTrackingProperties properties
    ) {
        this.videoService = videoService;
        this.videoViewMapper = videoViewMapper;
        this.qualifiedViewMapper = qualifiedViewMapper;
        this.redis = redis;
        this.properties = properties;
    }

    public ViewerIdentity resolveViewer(AuthPrincipal principal, String anonCookie) {
        if (principal != null) {
            return ViewerIdentity.authenticated(principal.userId());
        }
        return ViewerIdentity.parseUuid(anonCookie)
                .map(id -> ViewerIdentity.anonymous(id, false))
                .orElseGet(() -> ViewerIdentity.anonymous(UUID.randomUUID(), true));
    }

    @Transactional
    public QualifyViewResponse qualify(
            Long videoId,
            QualifyViewRequest request,
            ViewerIdentity viewer
    ) {
        Video video = videoService.requireVideo(videoId);
        UUID clientViewId = requireClientViewId(request);
        long watchedMs = requireWatchedMs(request);
        Long durationMs = ViewQualification.resolveDurationMs(video.getDurationSeconds(), request.durationMs());
        if (!ViewQualification.meets(watchedMs, durationMs, properties.qualifySeconds(), properties.qualifyPercent())) {
            throw new ApiException(
                    ErrorCode.VIEW_NOT_QUALIFIED,
                    HttpStatus.BAD_REQUEST,
                    "Watch time has not reached the qualified-view threshold"
            );
        }

        String dedupeKey = RedisKeys.viewDedupe(video.getId(), viewer.viewerKey());
        if (!redis.tryClaim(dedupeKey, properties.dedupeTtl())) {
            return alreadyCounted(video.getId());
        }

        int inserted = videoViewMapper.insertIdempotency(video.getId(), clientViewId);
        if (inserted == 0) {
            return alreadyCounted(video.getId());
        }

        videoViewMapper.incrementViewCount(video.getId());
        viewer.authenticatedUserId().ifPresent(userId ->
                qualifiedViewMapper.upsertIncrement(userId, video.getId()));
        return new QualifyViewResponse(true, false, currentCount(video.getId()));
    }

    public Map<Long, Long> viewCountsByIds(List<Long> ids) {
        Map<Long, Long> counts = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return counts;
        }
        for (VideoViewCount row : videoViewMapper.findViewCounts(ids)) {
            if (row.getVideoId() != null) {
                counts.put(row.getVideoId(), row.getViewCount());
            }
        }
        return counts;
    }

    private QualifyViewResponse alreadyCounted(Long videoId) {
        return new QualifyViewResponse(false, true, currentCount(videoId));
    }

    private long currentCount(Long videoId) {
        Long count = videoViewMapper.findViewCount(videoId);
        return count == null ? 0 : count;
    }

    private static UUID requireClientViewId(QualifyViewRequest request) {
        if (request == null) {
            throw invalid("clientViewId is required");
        }
        return ViewerIdentity.parseUuid(request.clientViewId())
                .orElseThrow(() -> invalid("clientViewId must be a UUID"));
    }

    private static long requireWatchedMs(QualifyViewRequest request) {
        if (request.watchedMs() == null || !ViewQualification.isWatchedMsUsable(request.watchedMs())) {
            throw invalid("watchedMs is invalid");
        }
        return request.watchedMs();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }
}
