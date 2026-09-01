package com.kiki.video.worker.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerMetricsTest {

    @Test
    void recordsJobOutcomesWithoutEntityIds() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WorkerMetrics metrics = new WorkerMetrics(registry);
        metrics.jobConsumed();
        metrics.jobSuccess();
        metrics.jobFailed();
        metrics.jobRetry();
        metrics.jobSkipped("ready");
        metrics.processingDuration(Duration.ofMillis(40));
        metrics.renditions(2);

        assertThat(registry.counter("kiki.worker.jobs", "result", "consumed").count()).isEqualTo(1.0);
        assertThat(registry.counter("kiki.worker.jobs", "result", "success").count()).isEqualTo(1.0);
        assertThat(registry.counter("kiki.worker.jobs", "result", "skipped_ready").count()).isEqualTo(1.0);
        assertThat(registry.find("kiki.worker.processing.duration").timer().count()).isEqualTo(1);
        assertThat(registry.find("kiki.worker.jobs").counters())
                .allMatch(counter -> counter.getId().getTags().stream().noneMatch(tag -> tag.getKey().equals("mediaObjectId")));
    }
}
