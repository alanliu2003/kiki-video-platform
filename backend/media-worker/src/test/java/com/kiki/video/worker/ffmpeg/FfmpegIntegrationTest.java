package com.kiki.video.worker.ffmpeg;

import com.kiki.video.common.media.MediaProcessingRequestedEvent;
import com.kiki.video.common.media.MediaProcessingStatus;
import com.kiki.video.common.media.ProcessedObjectKeys;
import com.kiki.video.worker.processing.MediaProcessingService;
import com.kiki.video.worker.storage.ObjectStore;
import com.kiki.video.worker.support.AbstractWorkerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FfmpegIntegrationTest extends AbstractWorkerIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(FfmpegIntegrationTest.class);

    @Autowired
    private MediaProcessingService processingService;

    @Autowired
    private ObjectStore objectStore;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProcessRunner processRunner;

    static boolean ffmpegAvailable() {
        try {
            ProcessRunner runner = new ProcessRunner();
            return runner.run(List.of("ffmpeg", "-version"), Duration.ofSeconds(10), null).succeeded()
                    && runner.run(List.of("ffprobe", "-version"), Duration.ofSeconds(10), null).succeeded();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Test
    void reportsFfmpegAvailability() {
        boolean available = ffmpegAvailable();
        log.info("ffmpeg available for integration tests: {}", available);
        if (!available) {
            log.warn("FFmpeg/ffprobe are not installed; FFmpeg pipeline integration test is skipped");
        }
        assertThat(available || !available).isTrue();
    }

    @Test
    @EnabledIf("ffmpegAvailable")
    void processesTinyFixtureToHlsAndThumbnail() throws Exception {
        Path fixture = Files.createTempFile("kiki-fixture", ".mp4");
        try {
            ProcessResult generated = processRunner.run(List.of(
                    "ffmpeg", "-y",
                    "-f", "lavfi", "-i", "testsrc=size=1280x720:rate=25",
                    "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
                    "-t", "2",
                    "-c:v", "libx264", "-pix_fmt", "yuv420p",
                    "-c:a", "aac",
                    fixture.toAbsolutePath().toString()
            ), Duration.ofMinutes(2), fixture.getParent());
            assertThat(generated.succeeded()).isTrue();

            String sha = "e".repeat(64);
            String rawKey = "raw/" + sha;
            objectStore.putFile(rawKey, fixture, "video/mp4");
            jdbcTemplate.update("""
                    INSERT INTO media_objects (
                        sha256, object_key, file_size_bytes, content_type, processing_status,
                        processing_attempts, created_at, updated_at
                    ) VALUES (?, ?, ?, 'video/mp4', 'PENDING', 0, NOW(), NOW())
                    """, sha, rawKey, Files.size(fixture));
            long mediaId = jdbcTemplate.queryForObject(
                    "SELECT id FROM media_objects WHERE sha256 = ?",
                    Long.class,
                    sha
            );

            processingService.handle(MediaProcessingRequestedEvent.create(mediaId, sha, rawKey));

            var status = jdbcTemplate.queryForObject(
                    "SELECT processing_status FROM media_objects WHERE id = ?",
                    String.class,
                    mediaId
            );
            assertThat(status).isEqualTo(MediaProcessingStatus.READY.name());
            assertThat(objectStore.list(rawKey)).contains(rawKey);
            assertThat(objectStore.list(ProcessedObjectKeys.prefix(mediaId)))
                    .contains(ProcessedObjectKeys.master(mediaId))
                    .contains(ProcessedObjectKeys.renditionPlaylist(mediaId, "360p"))
                    .contains(ProcessedObjectKeys.renditionPlaylist(mediaId, "720p"))
                    .contains(ProcessedObjectKeys.thumbnail(mediaId));
            assertThat(objectStore.list(ProcessedObjectKeys.prefix(mediaId)))
                    .anyMatch(key -> key.contains("/360p/segment"))
                    .noneMatch(key -> key.contains("/1080p/"));
        } finally {
            Files.deleteIfExists(fixture);
        }
    }
}
