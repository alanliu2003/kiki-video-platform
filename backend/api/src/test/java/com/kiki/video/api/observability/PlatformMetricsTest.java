package com.kiki.video.api.observability;

import com.kiki.video.api.notification.model.NotificationType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PlatformMetrics metrics = new PlatformMetrics(registry);

    @Test
    void incrementsLowCardinalityCounters() {
        metrics.uploadSessionInitiated();
        metrics.uploadCompleted(true, Duration.ofMillis(25));
        metrics.viewQualifyAccepted();
        metrics.viewQualifyAlreadyCounted();
        metrics.viewQualifyViewerWindow();
        metrics.viewQualifyRejected();
        metrics.searchRequest();
        metrics.searchUnavailable();
        metrics.searchIndexSuccess();
        metrics.searchIndexFailure();
        metrics.recommendationRequest(true);
        metrics.recommendationCacheHit();
        metrics.notificationCreated(NotificationType.VIDEO_LIKED);
        metrics.notificationReadAll();
        metrics.outboxPublishSuccess("media");
        metrics.outboxPublishFailure("search");
        metrics.redisFallback("cache_read");

        assertThat(counter("kiki.upload.sessions", "result", "initiated")).isEqualTo(1.0);
        assertThat(counter("kiki.upload.sessions", "result", "completed")).isEqualTo(1.0);
        assertThat(counter("kiki.upload.sessions", "result", "deduplicated")).isEqualTo(1.0);
        assertThat(counter("kiki.views.qualify", "result", "accepted")).isEqualTo(1.0);
        assertThat(counter("kiki.views.qualify", "result", "already_counted")).isEqualTo(1.0);
        assertThat(counter("kiki.views.qualify", "result", "viewer_window")).isEqualTo(1.0);
        assertThat(counter("kiki.views.qualify", "result", "rejected")).isEqualTo(1.0);
        assertThat(counter("kiki.search.requests")).isEqualTo(1.0);
        assertThat(counter("kiki.search.unavailable")).isEqualTo(1.0);
        assertThat(counter("kiki.recommendations.requests", "result", "cold_start")).isEqualTo(1.0);
        assertThat(counter("kiki.notifications.created", "type", "VIDEO_LIKED")).isEqualTo(1.0);
        assertThat(counter("kiki.outbox.publish", "outbox", "media", "result", "success")).isEqualTo(1.0);
        assertThat(registry.find("kiki.upload.complete.duration").timer()).isNotNull();
        assertThat(registry.find("kiki.upload.complete.duration").timer().count()).isEqualTo(1);
    }

    @Test
    void doesNotUseHighCardinalityTagKeys() {
        metrics.viewQualifyAccepted();
        assertThat(registry.find("kiki.views.qualify").counter().getId().getTags())
                .allMatch(tag -> !tag.getKey().toLowerCase().contains("id"))
                .allMatch(tag -> !tag.getValue().contains("/videos/"));
    }

    private double counter(String name, String... tags) {
        return registry.counter(name, tags).count();
    }
}
