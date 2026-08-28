package com.kiki.video.worker.processing;

import com.kiki.video.common.media.MediaProcessingStatus;
import com.kiki.video.worker.mapper.MediaProcessingMapper;
import com.kiki.video.worker.model.ProcessingMediaObject;
import com.kiki.video.worker.support.AbstractWorkerIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MediaProcessingClaimTest extends AbstractWorkerIntegrationTest {

    @Autowired
    private MediaProcessingMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void pendingMediaCanBeClaimedAndReadyCannot() {
        long pendingId = insertMedia("a".repeat(64), "PENDING");
        long readyId = insertMedia("b".repeat(64), "READY");

        Instant now = Instant.now();
        assertThat(mapper.claim(pendingId, 3, now.minusSeconds(60), now)).isEqualTo(1);
        assertThat(mapper.findById(pendingId).getProcessingStatus()).isEqualTo(MediaProcessingStatus.PROCESSING);
        assertThat(mapper.findById(pendingId).getProcessingAttempts()).isEqualTo(1);

        assertThat(mapper.claim(readyId, 3, now.minusSeconds(60), now)).isEqualTo(0);
        assertThat(mapper.findById(readyId).getProcessingStatus()).isEqualTo(MediaProcessingStatus.READY);
    }

    @Test
    void duplicateClaimDoesNotStartSecondProcessing() {
        long mediaId = insertMedia("c".repeat(64), "PENDING");
        Instant now = Instant.now();
        assertThat(mapper.claim(mediaId, 3, now.minusSeconds(60), now)).isEqualTo(1);
        assertThat(mapper.claim(mediaId, 3, now.minusSeconds(60), now)).isEqualTo(0);
        ProcessingMediaObject media = mapper.findById(mediaId);
        assertThat(media.getProcessingStatus()).isEqualTo(MediaProcessingStatus.PROCESSING);
        assertThat(media.getProcessingAttempts()).isEqualTo(1);
    }

    private long insertMedia(String sha256, String status) {
        jdbcTemplate.update("""
                INSERT INTO media_objects (
                    sha256, object_key, file_size_bytes, content_type, processing_status,
                    processing_attempts, created_at, updated_at
                ) VALUES (?, ?, 1024, 'video/mp4', ?, 0, NOW(), NOW())
                """, sha256, "raw/" + sha256, status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM media_objects WHERE sha256 = ?",
                Long.class,
                sha256
        );
    }
}
