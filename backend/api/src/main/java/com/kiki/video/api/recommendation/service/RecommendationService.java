package com.kiki.video.api.recommendation.service;

import com.kiki.video.api.config.RecommendationProperties;
import com.kiki.video.api.config.ViewTrackingProperties;
import com.kiki.video.api.interaction.cache.RedisKeys;
import com.kiki.video.api.recommendation.RecommendationReason;
import com.kiki.video.api.recommendation.RecommendationScore;
import com.kiki.video.api.recommendation.dto.RecommendationCardResponse;
import com.kiki.video.api.recommendation.dto.RecommendationFeedResponse;
import com.kiki.video.api.recommendation.mapper.RecommendationMapper;
import com.kiki.video.api.recommendation.mapper.UserVideoQualifiedViewMapper;
import com.kiki.video.api.recommendation.model.CreatorAffinityRow;
import com.kiki.video.api.recommendation.model.QualifiedViewRow;
import com.kiki.video.api.recommendation.model.RecommendationCandidateRow;
import com.kiki.video.api.video.dto.VideoOwnerResponse;
import com.kiki.video.api.view.cache.ViewTrackingRedisClient;
import com.kiki.video.api.view.mapper.VideoDiscoveryMapper;
import com.kiki.video.api.view.model.VideoDiscoveryRow;
import com.kiki.video.common.media.MediaProcessingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final RecommendationMapper recommendationMapper;
    private final UserVideoQualifiedViewMapper qualifiedViewMapper;
    private final VideoDiscoveryMapper discoveryMapper;
    private final ViewTrackingRedisClient redis;
    private final RecommendationProperties properties;
    private final ViewTrackingProperties viewProperties;
    private final ObjectMapper objectMapper;

    public RecommendationService(
            RecommendationMapper recommendationMapper,
            UserVideoQualifiedViewMapper qualifiedViewMapper,
            VideoDiscoveryMapper discoveryMapper,
            ViewTrackingRedisClient redis,
            RecommendationProperties properties,
            ViewTrackingProperties viewProperties,
            ObjectMapper objectMapper
    ) {
        this.recommendationMapper = recommendationMapper;
        this.qualifiedViewMapper = qualifiedViewMapper;
        this.discoveryMapper = discoveryMapper;
        this.redis = redis;
        this.properties = properties;
        this.viewProperties = viewProperties;
        this.objectMapper = objectMapper;
    }

    public RecommendationFeedResponse recommend(long userId, Integer page, Integer size) {
        PageBounds bounds = bounds(page, size);
        String cacheKey = RedisKeys.recommendationsPage(userId, bounds.page, bounds.size);
        RecommendationFeedResponse cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        RecommendationFeedResponse response = compute(userId, bounds);
        writeCache(cacheKey, response);
        return response;
    }

    private RecommendationFeedResponse compute(long userId, PageBounds bounds) {
        int historyLimit = Math.max(1, properties.historyLimit());
        int sourceLimit = Math.max(1, properties.sourceLimit());
        int candidateLimit = Math.max(bounds.size, properties.candidateLimit());
        int affinityCreatorLimit = Math.max(1, properties.affinityCreatorLimit());
        int heavySeenThreshold = Math.max(2, properties.heavySeenThreshold());

        List<Long> followedIds = recommendationMapper.findFollowedCreatorIds(userId);
        Set<Long> followed = new HashSet<>(followedIds);
        Map<Long, Double> affinityByCreator = new HashMap<>();
        for (CreatorAffinityRow row : recommendationMapper.findCreatorAffinities(
                userId, historyLimit, affinityCreatorLimit
        )) {
            if (row.getCreatorId() != null) {
                affinityByCreator.put(row.getCreatorId(), row.affinityPoints());
            }
        }
        Map<Long, Integer> seenCounts = new HashMap<>();
        for (QualifiedViewRow row : qualifiedViewMapper.findRecentByUser(userId, historyLimit)) {
            if (row.getVideoId() != null) {
                seenCounts.put(row.getVideoId(), row.getQualifiedViewCount());
            }
        }
        boolean coldStart = followed.isEmpty() && affinityByCreator.isEmpty() && seenCounts.isEmpty();

        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>();
        Set<Long> trendingIds = new HashSet<>();

        addBounded(candidateIds, videosFromOwners(followedIds, userId, sourceLimit), candidateLimit);
        addBounded(candidateIds, videosFromOwners(new ArrayList<>(affinityByCreator.keySet()), userId, sourceLimit), candidateLimit);

        List<Long> trending = discoveryMapper.findTrending(
                        sourceLimit,
                        0,
                        viewProperties.trendingViewWeight(),
                        viewProperties.trendingLikeWeight(),
                        viewProperties.trendingFavoriteWeight(),
                        viewProperties.trendingCommentWeight(),
                        viewProperties.trendingAgeDecay()
                )
                .stream()
                .map(VideoDiscoveryRow::getId)
                .filter(id -> id != null)
                .toList();
        trendingIds.addAll(trending);
        addBounded(candidateIds, trending, candidateLimit);

        List<Long> recent = discoveryMapper.findRecent(sourceLimit, 0)
                .stream()
                .map(VideoDiscoveryRow::getId)
                .filter(id -> id != null)
                .toList();
        addBounded(candidateIds, recent, candidateLimit);

        List<RecommendationCandidateRow> rows = candidateIds.isEmpty()
                ? List.of()
                : recommendationMapper.findCandidatesByIds(new ArrayList<>(candidateIds));

        List<ScoredCandidate> scored = new ArrayList<>();
        RecommendationScore.Weights weights = weights();
        Instant now = Instant.now();
        for (RecommendationCandidateRow row : rows) {
            if (row.getId() == null || row.getOwnerId() == null) {
                continue;
            }
            if (row.getOwnerId() == userId) {
                continue;
            }
            int seen = seenCounts.getOrDefault(row.getId(), 0);
            double affinity = affinityByCreator.getOrDefault(row.getOwnerId(), 0.0);
            boolean isFollowed = followed.contains(row.getOwnerId());
            double ageHours = ageHours(row.getCreatedAt(), now);
            double score = RecommendationScore.score(
                    new RecommendationScore.Signals(
                            affinity,
                            isFollowed,
                            row.getViewCount(),
                            row.getLikeCount(),
                            row.getFavoriteCount(),
                            row.getCommentCount(),
                            ageHours,
                            seen
                    ),
                    weights
            );
            String reason = RecommendationReason.resolve(
                    isFollowed,
                    affinity,
                    ageHours,
                    trendingIds.contains(row.getId())
            );
            scored.add(new ScoredCandidate(row, score, reason, seen));
        }

        applyHeavySeenExclusion(scored, bounds.size, heavySeenThreshold);

        scored.sort(Comparator
                .comparingDouble(ScoredCandidate::score).reversed()
                .thenComparing(candidate -> candidate.row().getId(), Comparator.reverseOrder()));

        long total = scored.size();
        int from = Math.min(bounds.offset, scored.size());
        int to = Math.min(from + bounds.size, scored.size());
        List<RecommendationCardResponse> items = scored.subList(from, to)
                .stream()
                .map(candidate -> toCard(candidate.row(), candidate.reason()))
                .toList();
        return new RecommendationFeedResponse(items, bounds.page, bounds.size, total, coldStart);
    }

    private void applyHeavySeenExclusion(List<ScoredCandidate> scored, int pageSize, int heavySeenThreshold) {
        List<ScoredCandidate> heavy = scored.stream()
                .filter(candidate -> candidate.seen() >= heavySeenThreshold)
                .toList();
        if (heavy.isEmpty()) {
            return;
        }
        int remaining = scored.size() - heavy.size();
        if (remaining >= pageSize) {
            scored.removeIf(candidate -> candidate.seen() >= heavySeenThreshold);
        }
    }

    private List<Long> videosFromOwners(List<Long> ownerIds, long userId, int limit) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return List.of();
        }
        return recommendationMapper.findRecentVideoIdsByOwners(ownerIds, userId, limit);
    }

    private static void addBounded(LinkedHashSet<Long> pool, List<Long> ids, int cap) {
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (pool.size() >= cap && !pool.contains(id)) {
                return;
            }
            pool.add(id);
        }
    }

    private RecommendationScore.Weights weights() {
        return new RecommendationScore.Weights(
                properties.affinityWeight(),
                properties.followedWeight(),
                properties.viewWeight(),
                properties.likeWeight(),
                properties.favoriteWeight(),
                properties.commentWeight(),
                properties.freshnessHours(),
                properties.freshnessWeight(),
                properties.seenPenalty(),
                properties.heavySeenPenalty()
        );
    }

    private RecommendationCardResponse toCard(RecommendationCandidateRow row, String reason) {
        MediaProcessingStatus processing = row.getProcessingStatus() == null
                ? MediaProcessingStatus.NOT_REQUESTED
                : row.getProcessingStatus();
        return new RecommendationCardResponse(
                row.getId(),
                row.getTitle(),
                new VideoOwnerResponse(row.getOwnerId(), row.getOwnerUsername(), row.getOwnerDisplayName()),
                row.getCreatedAt(),
                row.getDurationSeconds(),
                Boolean.TRUE.equals(row.getThumbnailAvailable())
                        ? "/api/videos/" + row.getId() + "/thumbnail"
                        : null,
                processing.name(),
                row.getViewCount(),
                row.getLikeCount(),
                reason
        );
    }

    private static double ageHours(Instant createdAt, Instant now) {
        if (createdAt == null) {
            return 0;
        }
        return Math.max(0, Duration.between(createdAt, now).toMillis() / 3_600_000.0);
    }

    private RecommendationFeedResponse readCache(String key) {
        return redis.get(key).map(json -> {
            try {
                return objectMapper.readValue(json, RecommendationFeedResponse.class);
            } catch (JacksonException ex) {
                log.warn("Ignoring unreadable recommendation cache for {}", key);
                return null;
            }
        }).orElse(null);
    }

    private void writeCache(String key, RecommendationFeedResponse response) {
        try {
            redis.set(key, objectMapper.writeValueAsString(response), properties.cacheTtl());
        } catch (JacksonException ex) {
            log.warn("Unable to serialize recommendation cache for {}", key);
        }
    }

    private PageBounds bounds(Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int max = Math.max(1, properties.maxPageSize());
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, max);
        return new PageBounds(safePage, safeSize, safePage * safeSize);
    }

    private record PageBounds(int page, int size, int offset) {
    }

    private record ScoredCandidate(
            RecommendationCandidateRow row,
            double score,
            String reason,
            int seen
    ) {
    }
}
