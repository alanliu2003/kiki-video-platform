package com.kiki.video.api.observability;

import com.kiki.video.api.media.mapper.MediaProcessingOutboxMapper;
import com.kiki.video.api.search.mapper.SearchIndexOutboxMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OutboxBacklogSampler {

    private static final Logger log = LoggerFactory.getLogger(OutboxBacklogSampler.class);

    private final MediaProcessingOutboxMapper mediaOutboxMapper;
    private final SearchIndexOutboxMapper searchOutboxMapper;
    private final AtomicLong mediaPending = new AtomicLong();
    private final AtomicLong searchPending = new AtomicLong();
    private final AtomicLong mediaOldestAgeSeconds = new AtomicLong();
    private final AtomicLong searchOldestAgeSeconds = new AtomicLong();

    public OutboxBacklogSampler(
            MediaProcessingOutboxMapper mediaOutboxMapper,
            SearchIndexOutboxMapper searchOutboxMapper,
            MeterRegistry registry
    ) {
        this.mediaOutboxMapper = mediaOutboxMapper;
        this.searchOutboxMapper = searchOutboxMapper;
        Gauge.builder("kiki.outbox.pending", mediaPending, AtomicLong::doubleValue)
                .description("Sampled pending/publishing media processing outbox rows")
                .tag(PlatformMetrics.OUTBOX, "media")
                .register(registry);
        Gauge.builder("kiki.outbox.pending", searchPending, AtomicLong::doubleValue)
                .description("Sampled pending/publishing search index outbox rows")
                .tag(PlatformMetrics.OUTBOX, "search")
                .register(registry);
        Gauge.builder("kiki.outbox.oldest.pending.age.seconds", mediaOldestAgeSeconds, AtomicLong::doubleValue)
                .description("Age of the oldest pending media outbox row")
                .tag(PlatformMetrics.OUTBOX, "media")
                .register(registry);
        Gauge.builder("kiki.outbox.oldest.pending.age.seconds", searchOldestAgeSeconds, AtomicLong::doubleValue)
                .description("Age of the oldest pending search outbox row")
                .tag(PlatformMetrics.OUTBOX, "search")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${app.observability.outbox-sample-interval:15s}")
    public void sample() {
        try {
            Instant now = Instant.now();
            mediaPending.set(Math.max(0, mediaOutboxMapper.countPending()));
            searchPending.set(Math.max(0, searchOutboxMapper.countPending()));
            mediaOldestAgeSeconds.set(ageSeconds(mediaOutboxMapper.oldestPendingCreatedAt(), now));
            searchOldestAgeSeconds.set(ageSeconds(searchOutboxMapper.oldestPendingCreatedAt(), now));
        } catch (RuntimeException ex) {
            log.warn("outbox backlog sample failed reason={}", ex.getMessage());
        }
    }

    static long ageSeconds(Instant createdAt, Instant now) {
        if (createdAt == null || now == null || createdAt.isAfter(now)) {
            return 0;
        }
        return Math.max(0, Duration.between(createdAt, now).toSeconds());
    }
}
