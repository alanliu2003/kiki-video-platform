package com.kiki.video.api.search.service;

import com.kiki.video.api.config.ElasticsearchProperties;
import com.kiki.video.api.config.SearchProperties;
import com.kiki.video.api.exception.ApiException;
import com.kiki.video.api.exception.ErrorCode;
import com.kiki.video.api.observability.PlatformMetrics;
import com.kiki.video.api.search.dto.SearchRebuildReport;
import com.kiki.video.api.search.index.ElasticsearchVideoSearchIndex;
import com.kiki.video.api.search.index.SearchIndexException;
import com.kiki.video.api.search.index.VideoSearchDocument;
import com.kiki.video.api.search.index.VideoSearchIndex;
import com.kiki.video.api.search.mapper.SearchVideoMapper;
import com.kiki.video.api.search.model.SearchVideoRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class SearchRebuildService {

    private static final Logger log = LoggerFactory.getLogger(SearchRebuildService.class);

    private final SearchVideoMapper searchVideoMapper;
    private final VideoSearchIndex videoSearchIndex;
    private final ElasticsearchProperties elasticsearchProperties;
    private final SearchProperties searchProperties;
    private final PlatformMetrics metrics;

    public SearchRebuildService(
            SearchVideoMapper searchVideoMapper,
            VideoSearchIndex videoSearchIndex,
            ElasticsearchProperties elasticsearchProperties,
            SearchProperties searchProperties,
            PlatformMetrics metrics
    ) {
        this.searchVideoMapper = searchVideoMapper;
        this.videoSearchIndex = videoSearchIndex;
        this.elasticsearchProperties = elasticsearchProperties;
        this.searchProperties = searchProperties;
        this.metrics = metrics;
    }

    public SearchRebuildReport rebuild() {
        if (!elasticsearchProperties.enabled() || !videoSearchIndex.isAvailable()) {
            throw new ApiException(
                    ErrorCode.SEARCH_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Search rebuild requires Elasticsearch"
            );
        }
        Instant started = Instant.now();
        String targetIndex = createTargetIndex();
        int batchSize = Math.max(1, searchProperties.rebuildBatchSize());
        long eligible = searchVideoMapper.countEligible();
        int indexed = 0;
        int failed = 0;
        long afterId = 0;
        while (true) {
            List<SearchVideoRow> rows = searchVideoMapper.findAfterId(afterId, batchSize);
            if (rows.isEmpty()) {
                break;
            }
            List<VideoSearchDocument> documents = new ArrayList<>(rows.size());
            for (SearchVideoRow row : rows) {
                documents.add(row.toDocument());
                afterId = row.getVideoId();
            }
            try {
                VideoSearchIndex.BulkIndexResult result = videoSearchIndex.bulkUpsert(targetIndex, documents);
                indexed += result.indexed();
                failed += result.failed();
            } catch (SearchIndexException ex) {
                failed += documents.size();
                log.warn("search rebuild batch failed afterId={} size={}", afterId, documents.size(), ex);
            }
        }
        if (failed > 0) {
            throw new IllegalStateException(
                    "Search rebuild failed indexed=" + indexed + " failed=" + failed + " index=" + targetIndex
            );
        }
        switchAlias(targetIndex);
        videoSearchIndex.refresh();
        long durationMs = Duration.between(started, Instant.now()).toMillis();
        SearchRebuildReport report = new SearchRebuildReport(
                targetIndex,
                elasticsearchProperties.videoIndexAlias(),
                eligible,
                indexed,
                failed,
                durationMs
        );
        metrics.searchRebuild(Duration.ofMillis(durationMs), indexed);
        log.info(
                "search rebuild complete index={} alias={} eligible={} indexed={} failed={} durationMs={}",
                report.indexName(),
                report.alias(),
                report.eligible(),
                report.indexed(),
                report.failed(),
                report.durationMs()
        );
        return report;
    }

    private String createTargetIndex() {
        if (videoSearchIndex instanceof ElasticsearchVideoSearchIndex elastic) {
            return elastic.createRebuildIndex();
        }
        videoSearchIndex.ensureWritableIndex();
        return elasticsearchProperties.videoIndexVersion();
    }

    private void switchAlias(String targetIndex) {
        if (videoSearchIndex instanceof ElasticsearchVideoSearchIndex elastic) {
            elastic.switchAlias(targetIndex);
            return;
        }
        videoSearchIndex.ensureWritableIndex();
    }
}
