package com.kiki.video.api.media;

import com.kiki.video.api.config.MediaProcessingProperties;
import com.kiki.video.api.media.mapper.MediaProcessingOutboxMapper;
import com.kiki.video.api.observability.PlatformMetrics;
import com.kiki.video.api.media.model.MediaProcessingOutbox;
import com.kiki.video.common.media.MediaProcessingRequestedEvent;
import com.kiki.video.common.media.ProcessingDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class MediaProcessingOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingOutboxPublisher.class);

    private final MediaProcessingOutboxMapper outboxMapper;
    private final MediaProcessingPublisher publisher;
    private final MediaProcessingProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final PlatformMetrics metrics;

    public MediaProcessingOutboxPublisher(
            MediaProcessingOutboxMapper outboxMapper,
            MediaProcessingPublisher publisher,
            MediaProcessingProperties properties,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            PlatformMetrics metrics
    ) {
        this.outboxMapper = outboxMapper;
        this.publisher = publisher;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${app.media.outbox-poll-interval:5s}")
    public void publishDue() {
        Instant now = Instant.now();
        Instant staleBefore = now.minus(properties.stalePublishingAfter());
        List<MediaProcessingOutbox> claimed = transactionTemplate.execute(status ->
                outboxMapper.claimDue(now, staleBefore, properties.outboxBatchSize()));
        if (claimed == null || claimed.isEmpty()) {
            return;
        }
        for (MediaProcessingOutbox row : claimed) {
            publishOne(row);
        }
    }

    void publishOne(MediaProcessingOutbox row) {
        Instant now = Instant.now();
        try {
            MediaProcessingRequestedEvent event = objectMapper.readValue(
                    row.getPayload(),
                    MediaProcessingRequestedEvent.class
            );
            publisher.publishProcessingRequested(event);
            outboxMapper.markPublished(row.getId(), now);
            metrics.outboxPublishSuccess("media");
            log.info(
                    "media processing outbox published outboxId={} mediaObjectId={}",
                    row.getId(),
                    row.getMediaObjectId()
            );
        } catch (RuntimeException ex) {
            Instant nextAttempt = now.plus(backoff(row.getAttemptCount()));
            outboxMapper.markRetry(
                    row.getId(),
                    nextAttempt,
                    ProcessingDiagnostics.truncate(ex.getMessage()),
                    now
            );
            metrics.outboxPublishFailure("media");
            log.warn(
                    "media processing outbox retry outboxId={} mediaObjectId={} attempt={} nextAttemptAt={}",
                    row.getId(),
                    row.getMediaObjectId(),
                    row.getAttemptCount(),
                    nextAttempt
            );
        }
    }

    static Duration backoff(int attemptCount) {
        int exponent = Math.max(0, Math.min(attemptCount - 1, 6));
        long seconds = Math.min(300, 5L * (1L << exponent));
        return Duration.ofSeconds(seconds);
    }
}
