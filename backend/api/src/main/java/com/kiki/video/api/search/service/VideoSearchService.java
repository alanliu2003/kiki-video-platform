package com.kiki.video.api.search.service;

import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.observability.PlatformMetrics;
import com.kiki.video.api.search.dto.HighlightSpan;
import com.kiki.video.api.search.dto.SearchHighlights;
import com.kiki.video.api.search.dto.SearchOwnerResponse;
import com.kiki.video.api.search.dto.VideoSearchItemResponse;
import com.kiki.video.api.search.dto.VideoSearchResponse;
import com.kiki.video.api.search.dto.VideoSearchSort;
import com.kiki.video.api.search.highlight.HighlightParser;
import com.kiki.video.api.search.index.SearchIndexException;
import com.kiki.video.api.search.index.VideoSearchDocument;
import com.kiki.video.api.search.index.VideoSearchHits;
import com.kiki.video.api.search.index.VideoSearchIndex;
import com.kiki.video.api.search.index.VideoSearchQuery;
import com.kiki.video.api.view.service.ViewTrackingService;
import com.kiki.video.common.media.MediaProcessingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class VideoSearchService {

    private static final Logger log = LoggerFactory.getLogger(VideoSearchService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int SNIPPET_LENGTH = 180;

    private final VideoSearchIndex videoSearchIndex;
    private final ViewTrackingService viewTrackingService;
    private final PlatformMetrics metrics;

    public VideoSearchService(
            VideoSearchIndex videoSearchIndex,
            ViewTrackingService viewTrackingService,
            PlatformMetrics metrics
    ) {
        this.videoSearchIndex = videoSearchIndex;
        this.viewTrackingService = viewTrackingService;
        this.metrics = metrics;
    }

    public VideoSearchResponse search(
            String q,
            Integer page,
            Integer size,
            String sort,
            Long ownerId,
            String processingStatus,
            Instant createdAfter,
            Instant createdBefore
    ) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_SEARCH_QUERY, HttpStatus.BAD_REQUEST, "Search query is required");
        }
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        if ((long) safePage * safeSize >= 10_000) {
            throw new ApiException(ErrorCode.INVALID_SEARCH_QUERY, HttpStatus.BAD_REQUEST, "Search page is too deep");
        }
        VideoSearchSort parsedSort = parseSort(sort);
        String normalizedStatus = normalizeProcessingStatus(processingStatus);
        metrics.searchRequest();
        if (!videoSearchIndex.isAvailable()) {
            throw unavailable();
        }
        try {
            VideoSearchHits hits = videoSearchIndex.search(new VideoSearchQuery(
                    query,
                    safePage,
                    safeSize,
                    parsedSort,
                    ownerId,
                    normalizedStatus,
                    createdAfter,
                    createdBefore
            ));
            List<VideoSearchItemResponse> items = withViewCounts(hits.items().stream()
                    .map(this::toItem)
                    .toList());
            log.info(
                    "video search qLength={} page={} size={} sort={} total={} tookMs={}",
                    query.length(),
                    safePage,
                    safeSize,
                    parsedSort,
                    hits.total(),
                    hits.tookMs()
            );
            return new VideoSearchResponse(items, safePage, safeSize, hits.total(), hits.tookMs());
        } catch (SearchIndexException ex) {
            log.warn("video search unavailable: {}", ex.getMessage());
            throw unavailable();
        }
    }

    private VideoSearchItemResponse toItem(VideoSearchHits.Hit hit) {
        VideoSearchDocument document = hit.document();
        List<HighlightSpan> title = firstHighlight(hit, "title");
        List<HighlightSpan> description = firstHighlight(hit, "description");
        List<HighlightSpan> ownerUsername = firstHighlight(hit, "ownerUsername");
        List<HighlightSpan> ownerDisplayName = firstHighlight(hit, "ownerDisplayName");
        String snippet = description.isEmpty()
                ? truncate(document.description())
                : HighlightParser.plainText(description);
        return new VideoSearchItemResponse(
                document.videoId(),
                document.title(),
                snippet,
                new SearchOwnerResponse(document.ownerId(), document.ownerUsername(), document.ownerDisplayName()),
                document.createdAt(),
                document.durationSeconds(),
                Boolean.TRUE.equals(document.thumbnailAvailable())
                        ? "/api/videos/" + document.videoId() + "/thumbnail"
                        : null,
                document.processingStatus(),
                new SearchHighlights(title, description, ownerUsername, ownerDisplayName),
                0L
        );
    }

    private List<VideoSearchItemResponse> withViewCounts(List<VideoSearchItemResponse> items) {
        Map<Long, Long> counts = viewTrackingService.viewCountsByIds(
                items.stream().map(VideoSearchItemResponse::videoId).toList()
        );
        return items.stream()
                .map(item -> new VideoSearchItemResponse(
                        item.videoId(),
                        item.title(),
                        item.descriptionSnippet(),
                        item.owner(),
                        item.createdAt(),
                        item.durationSeconds(),
                        item.thumbnailUrl(),
                        item.processingStatus(),
                        item.highlights(),
                        counts.getOrDefault(item.videoId(), 0L)
                ))
                .toList();
    }

    private static List<HighlightSpan> firstHighlight(VideoSearchHits.Hit hit, String field) {
        List<String> fragments = hit.highlightFragments().get(field);
        if (fragments == null || fragments.isEmpty()) {
            return List.of();
        }
        return HighlightParser.parse(fragments.getFirst());
    }

    private static String truncate(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String trimmed = description.strip();
        if (trimmed.length() <= SNIPPET_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, SNIPPET_LENGTH).stripTrailing() + "…";
    }

    private static VideoSearchSort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return VideoSearchSort.RELEVANCE;
        }
        try {
            return VideoSearchSort.valueOf(sort.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.INVALID_SEARCH_QUERY, HttpStatus.BAD_REQUEST, "Unsupported search sort");
        }
    }

    private static String normalizeProcessingStatus(String processingStatus) {
        if (processingStatus == null || processingStatus.isBlank()) {
            return null;
        }
        try {
            return MediaProcessingStatus.valueOf(processingStatus.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.INVALID_SEARCH_QUERY, HttpStatus.BAD_REQUEST, "Unsupported processingStatus filter");
        }
    }

    private ApiException unavailable() {
        metrics.searchUnavailable();
        return new ApiException(
                ErrorCode.SEARCH_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Search is temporarily unavailable"
        );
    }
}
