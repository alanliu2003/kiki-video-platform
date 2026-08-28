package com.kiki.video.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.media")
public record WorkerMediaProperties(
        String ffmpegPath,
        String ffprobePath,
        String tempDir,
        Duration timeout,
        int hlsSegmentDuration,
        int maxAttempts,
        Duration staleProcessingAfter,
        Duration retryBackoff,
        boolean verifyFfmpeg
) {

    public Path tempDirectory() {
        if (tempDir == null || tempDir.isBlank()) {
            return Path.of(System.getProperty("java.io.tmpdir"), "kiki-media-worker");
        }
        return Path.of(tempDir);
    }
}
