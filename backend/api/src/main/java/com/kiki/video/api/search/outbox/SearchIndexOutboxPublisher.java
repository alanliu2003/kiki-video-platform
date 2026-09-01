package com.kiki.video.api.search.outbox;

import com.kiki.video.api.config.ElasticsearchProperties;
import com.kiki.video.api.config.SearchProperties;
import com.kiki.video.api.observability.PlatformMetrics;
import com.kiki.video.api.search.index.VideoSearchIndex;
import com.kiki.video.api.search.mapper.SearchIndexOutboxMapper;
import com.kiki.video.api.search.mapper.SearchVideoMapper;
import com.kiki.video.api.search.model.SearchIndexOutbox;
import com.kiki.video.api.search.model.SearchVideoRow;
import com.kiki.video.common.media.ProcessingDiagnostics;
import com.kiki.video.common.search.VideoSearchIndexEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class SearchIndexOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexOutboxPublisher.class);

    private final SearchIndexOutboxMapper outboxMapper;
    private final SearchVideoMapper searchVideoMapper;
    private final VideoSearchIndex videoSearchIndex;
    private final SearchProperties searchProperties;
    private final ElasticsearchProperties elasticsearchProperties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final PlatformMetrics metrics;

    public SearchIndexOutboxPublisher(
            SearchIndexOutboxMapper outboxMapper,
            SearchVideoMapper searchVideoMapper,
            VideoSearchIndex videoSearchIndex,
            SearchProperties searchProperties,
            ElasticsearchProperties elasticsearchProperties,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            PlatformMetrics metrics
    ) {
        this.outboxMapper = outboxMapper;
        this.searchVideoMapper = searchVideoMapper;
        this.videoSearchIndex = videoSearchIndex;
        this.searchProperties = searchProperties;
        this.elasticsearchProperties = elasticsearchProperties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${app.search.outbox-poll-interval:5s}")
    public void publishDue() {
        if (!elasticsearchProperties.enabled()) {
            return;
        }
        Instant now = Instant.now();
        Instant staleBefore = now.minus(searchProperties.stalePublishingAfter());
        List<SearchIndexOutbox> claimed = transactionTemplate.execute(status ->
                outboxMapper.claimDue(now, staleBefore, searchProperties.outboxBatchSize()));
        if (claimed == null || claimed.isEmpty()) {
            return;
        }
        for (SearchIndexOutbox row : claimed) {
            publishOne(row);
        }
    }

    void publishOne(SearchIndexOutbox row) {
        Instant now = Instant.now();
        try {
            VideoSearchIndexEvent event = objectMapper.readValue(row.getPayload(), VideoSearchIndexEvent.class);
            project(event, row.getEventType());
            outboxMapper.markPublished(row.getId(), now);
            metrics.searchIndexSuccess();
            metrics.outboxPublishSuccess("search");
            log.info(
                    "search index outbox published outboxId={} videoId={} eventType={}",
                    row.getId(),
                    row.getVideoId(),
                    row.getEventType()
            );
        } catch (RuntimeException ex) {
            Instant nextAttempt = now.plus(backoff(row.getAttemptCount()));
            outboxMapper.markRetry(
                    row.getId(),
                    nextAttempt,
                    ProcessingDiagnostics.truncate(ex.getMessage()),
                    now
            );
            metrics.searchIndexFailure();
            metrics.outboxPublishFailure("search");
            log.warn(
                    "search outbox retry outboxId={} videoId={} attempt={} nextAttemptAt={}",
                    row.getId(),
                    row.getVideoId(),
                    row.getAttemptCount(),
                    nextAttempt
            );
        }
    }

    private void project(VideoSearchIndexEvent event, String eventType) {
        if (VideoSearchIndexEvent.DELETE.equals(eventType)) {
            videoSearchIndex.delete(event.videoId());
            return;
        }
        SearchVideoRow row = searchVideoMapper.findByVideoId(event.videoId());
        if (row == null) {
            videoSearchIndex.delete(event.videoId());
            return;
        }
        videoSearchIndex.upsert(row.toDocument());
    }

    static Duration backoff(int attemptCount) {
        int exponent = Math.max(0, Math.min(attemptCount - 1, 6));
        long seconds = Math.min(300, 5L * (1L << exponent));
        return Duration.ofSeconds(seconds);
    }
}
