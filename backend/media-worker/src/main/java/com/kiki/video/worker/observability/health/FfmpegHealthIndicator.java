package com.kiki.video.worker.observability.health;

import com.kiki.video.worker.config.WorkerMediaProperties;
import com.kiki.video.worker.ffmpeg.ProcessResult;
import com.kiki.video.worker.ffmpeg.ProcessRunner;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component("ffmpeg")
public class FfmpegHealthIndicator implements HealthIndicator {

    private final WorkerMediaProperties properties;
    private final ProcessRunner processRunner;

    public FfmpegHealthIndicator(WorkerMediaProperties properties, ProcessRunner processRunner) {
        this.properties = properties;
        this.processRunner = processRunner;
    }

    @Override
    public Health health() {
        try {
            ProcessResult ffmpeg = processRunner.run(List.of(properties.ffmpegPath(), "-version"), Duration.ofSeconds(3), null);
            ProcessResult ffprobe = processRunner.run(List.of(properties.ffprobePath(), "-version"), Duration.ofSeconds(3), null);
            if (ffmpeg.succeeded() && ffprobe.succeeded()) {
                return DependencyHealth.up("ffmpeg");
            }
            String missing = !ffmpeg.succeeded() && !ffprobe.succeeded()
                    ? "ffmpeg and ffprobe unavailable"
                    : !ffmpeg.succeeded() ? "ffmpeg unavailable" : "ffprobe unavailable";
            return DependencyHealth.down("ffmpeg", missing);
        } catch (RuntimeException ex) {
            return DependencyHealth.down("ffmpeg", ex.getMessage());
        }
    }
}
