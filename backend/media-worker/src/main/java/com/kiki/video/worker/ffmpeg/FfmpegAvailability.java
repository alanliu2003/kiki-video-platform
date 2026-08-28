package com.kiki.video.worker.ffmpeg;

import com.kiki.video.worker.config.WorkerMediaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@Order(1)
@ConditionalOnProperty(prefix = "app.media", name = "verify-ffmpeg", havingValue = "true", matchIfMissing = true)
public class FfmpegAvailability implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FfmpegAvailability.class);

    private final WorkerMediaProperties properties;
    private final ProcessRunner processRunner;

    public FfmpegAvailability(WorkerMediaProperties properties, ProcessRunner processRunner) {
        this.properties = properties;
        this.processRunner = processRunner;
    }

    @Override
    public void run(ApplicationArguments args) {
        verify(properties.ffmpegPath(), "ffmpeg");
        verify(properties.ffprobePath(), "ffprobe");
        log.info("FFmpeg tools are available ffmpeg={} ffprobe={}", properties.ffmpegPath(), properties.ffprobePath());
    }

    private void verify(String executable, String label) {
        ProcessResult result = processRunner.run(List.of(executable, "-version"), Duration.ofSeconds(10), null);
        if (!result.succeeded()) {
            throw new IllegalStateException(
                    label + " is not available on PATH. Install FFmpeg locally or run the worker image."
            );
        }
    }
}
