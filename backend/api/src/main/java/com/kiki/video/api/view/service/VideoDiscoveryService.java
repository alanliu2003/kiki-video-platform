package com.kiki.video.api.view.service;

import com.kiki.video.api.config.ViewTrackingProperties;
import com.kiki.video.api.interaction.cache.RedisKeys;
import com.kiki.video.api.video.delivery.MediaDeliveryService;
import com.kiki.video.api.video.dto.VideoOwnerResponse;
import com.kiki.video.api.view.cache.ViewTrackingRedisClient;
import com.kiki.video.api.view.dto.VideoCardResponse;
import com.kiki.video.api.view.dto.VideoFeedResponse;
import com.kiki.video.api.view.mapper.VideoDiscoveryMapper;
import com.kiki.video.api.view.model.VideoDiscoveryRow;
import com.kiki.video.common.media.MediaProcessingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class VideoDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(VideoDiscoveryService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final VideoDiscoveryMapper discoveryMapper;
    private final ViewTrackingRedisClient redis;
    private final ViewTrackingProperties properties;
    private final ObjectMapper objectMapper;
    private final MediaDeliveryService mediaDeliveryService;

    public VideoDiscoveryService(
            VideoDiscoveryMapper discoveryMapper,
            ViewTrackingRedisClient redis,
            ViewTrackingProperties properties,
            ObjectMapper objectMapper,
            MediaDeliveryService mediaDeliveryService
    ) {
        this.discoveryMapper = discoveryMapper;
        this.redis = redis;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.mediaDeliveryService = mediaDeliveryService;
    }

    public VideoFeedResponse trending(Integer page, Integer size) {
        PageBounds bounds = bounds(page, size);
        String cacheKey = RedisKeys.trendingPage(bounds.page, bounds.size);
        VideoFeedResponse cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        List<VideoCardResponse> items = discoveryMapper.findTrending(
                        bounds.size,
                        bounds.offset,
                        properties.trendingViewWeight(),
                        properties.trendingLikeWeight(),
                        properties.trendingFavoriteWeight(),
                        properties.trendingCommentWeight(),
                        properties.trendingAgeDecay()
                )
                .stream()
                .map(this::toCard)
                .toList();
        VideoFeedResponse response = new VideoFeedResponse(items, bounds.page, bounds.size, discoveryMapper.countVideos());
        writeCache(cacheKey, response);
        return response;
    }

    public VideoFeedResponse recent(Integer page, Integer size) {
        PageBounds bounds = bounds(page, size);
        List<VideoCardResponse> items = discoveryMapper.findRecent(bounds.size, bounds.offset)
                .stream()
                .map(this::toCard)
                .toList();
        return new VideoFeedResponse(items, bounds.page, bounds.size, discoveryMapper.countVideos());
    }

    private VideoFeedResponse readCache(String key) {
        return redis.get(key).map(json -> {
            try {
                return objectMapper.readValue(json, VideoFeedResponse.class);
            } catch (JacksonException ex) {
                log.warn("Ignoring unreadable trending cache for {}", key);
                return null;
            }
        }).orElse(null);
    }

    private void writeCache(String key, VideoFeedResponse response) {
        try {
            redis.set(key, objectMapper.writeValueAsString(response), properties.trendingCacheTtl());
        } catch (JacksonException ex) {
            log.warn("Unable to serialize trending cache for {}", key);
        }
    }

    private VideoCardResponse toCard(VideoDiscoveryRow row) {
        MediaProcessingStatus processing = row.getProcessingStatus() == null
                ? MediaProcessingStatus.NOT_REQUESTED
                : row.getProcessingStatus();
        return new VideoCardResponse(
                row.getId(),
                row.getTitle(),
                new VideoOwnerResponse(row.getOwnerId(), row.getOwnerUsername(), row.getOwnerDisplayName()),
                row.getCreatedAt(),
                row.getDurationSeconds(),
                mediaDeliveryService.cardThumbnailUrl(row.getId(), Boolean.TRUE.equals(row.getThumbnailAvailable())),
                processing.name(),
                row.getViewCount(),
                row.getLikeCount()
        );
    }

    private PageBounds bounds(Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int max = Math.max(1, properties.maxPageSize());
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, max);
        return new PageBounds(safePage, safeSize, safePage * safeSize);
    }

    private record PageBounds(int page, int size, int offset) {
    }
}
