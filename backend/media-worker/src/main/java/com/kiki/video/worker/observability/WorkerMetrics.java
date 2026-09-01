package com.kiki.video.worker.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class WorkerMetrics {

    public static final String RESULT = "result";

    private final MeterRegistry registry;

    public WorkerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void jobConsumed() {
        increment("kiki.worker.jobs", RESULT, "consumed");
    }

    public void jobSuccess() {
        increment("kiki.worker.jobs", RESULT, "success");
    }

    public void jobFailed() {
        increment("kiki.worker.jobs", RESULT, "failed");
    }

    public void jobRetry() {
        increment("kiki.worker.jobs", RESULT, "retry");
    }

    public void jobSkipped(String reason) {
        increment("kiki.worker.jobs", RESULT, "skipped_" + reason);
    }

    public void processingDuration(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return;
        }
        Timer.builder("kiki.worker.processing.duration")
                .description("Media processing wall time for claimed jobs")
                .register(registry)
                .record(duration);
    }

    public void renditions(int count) {
        Counter.builder("kiki.worker.renditions")
                .description("HLS renditions produced by successful jobs")
                .register(registry)
                .increment(Math.max(0, count));
    }

    private void increment(String name, String... tags) {
        Counter.Builder builder = Counter.builder(name);
        for (int i = 0; i + 1 < tags.length; i += 2) {
            builder.tag(tags[i], tags[i + 1]);
        }
        builder.register(registry).increment();
    }
}
