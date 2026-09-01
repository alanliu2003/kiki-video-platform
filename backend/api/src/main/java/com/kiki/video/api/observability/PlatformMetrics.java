package com.kiki.video.api.observability;

import com.kiki.video.api.notification.model.NotificationType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PlatformMetrics {

    public static final String RESULT = "result";
    public static final String TYPE = "type";
    public static final String OUTBOX = "outbox";

    private final MeterRegistry registry;

    public PlatformMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void uploadSessionInitiated() {
        increment("kiki.upload.sessions", RESULT, "initiated");
    }

    public void uploadCompleted(boolean deduplicated, Duration duration) {
        increment("kiki.upload.sessions", RESULT, "completed");
        if (deduplicated) {
            increment("kiki.upload.sessions", RESULT, "deduplicated");
        }
        if (duration != null && !duration.isNegative()) {
            Timer.builder("kiki.upload.complete.duration")
                    .description("Time from upload session creation to completion")
                    .register(registry)
                    .record(duration);
        }
    }

    public void mediaJobStarted() {
        increment("kiki.media.jobs", RESULT, "started");
    }

    public void searchRequest() {
        increment("kiki.search.requests");
    }

    public void searchUnavailable() {
        increment("kiki.search.unavailable");
    }

    public void searchIndexSuccess() {
        increment("kiki.search.index", RESULT, "success");
    }

    public void searchIndexFailure() {
        increment("kiki.search.index", RESULT, "failure");
    }

    public void searchRebuild(Duration duration, int documents) {
        if (duration != null && !duration.isNegative()) {
            Timer.builder("kiki.search.rebuild.duration")
                    .description("Full search index rebuild duration")
                    .register(registry)
                    .record(duration);
        }
        Counter.builder("kiki.search.rebuild.documents")
                .description("Documents included in a search rebuild")
                .register(registry)
                .increment(Math.max(0, documents));
    }

    public void viewQualifyAccepted() {
        increment("kiki.views.qualify", RESULT, "accepted");
    }

    public void viewQualifyAlreadyCounted() {
        increment("kiki.views.qualify", RESULT, "already_counted");
    }

    public void viewQualifyViewerWindow() {
        increment("kiki.views.qualify", RESULT, "viewer_window");
    }

    public void viewQualifyRejected() {
        increment("kiki.views.qualify", RESULT, "rejected");
    }

    public void recommendationRequest(boolean coldStart) {
        increment("kiki.recommendations.requests", RESULT, coldStart ? "cold_start" : "personalized");
    }

    public void recommendationCacheHit() {
        increment("kiki.recommendations.cache", RESULT, "hit");
    }

    public void recommendationCacheMiss() {
        increment("kiki.recommendations.cache", RESULT, "miss");
    }

    public void redisFallback(String operation) {
        increment("kiki.redis.fallback", "operation", sanitizeTag(operation));
    }

    public void notificationCreated(NotificationType type) {
        increment("kiki.notifications.created", TYPE, type.name());
    }

    public void notificationReadOne() {
        increment("kiki.notifications.read", RESULT, "one");
    }

    public void notificationReadAll() {
        increment("kiki.notifications.read", RESULT, "all");
    }

    public void outboxPublishSuccess(String outbox) {
        increment("kiki.outbox.publish", OUTBOX, sanitizeTag(outbox), RESULT, "success");
    }

    public void outboxPublishFailure(String outbox) {
        increment("kiki.outbox.publish", OUTBOX, sanitizeTag(outbox), RESULT, "failure");
    }

    public void increment(String name, String... tags) {
        Counter.Builder builder = Counter.builder(name);
        for (int i = 0; i + 1 < tags.length; i += 2) {
            builder.tag(tags[i], tags[i + 1]);
        }
        builder.register(registry).increment();
    }

    private static String sanitizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() <= 32 ? value : value.substring(0, 32);
    }
}
