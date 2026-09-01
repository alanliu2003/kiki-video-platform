package com.kiki.video.api.media;

import com.kiki.video.api.config.MediaProcessingProperties;
import com.kiki.video.api.media.mapper.MediaProcessingOutboxMapper;
import com.kiki.video.api.media.model.MediaProcessingOutbox;
import com.kiki.video.api.observability.PlatformMetrics;
import com.kiki.video.common.media.MediaProcessingRequestedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaProcessingOutboxPublisherTest {

    @Mock
    private MediaProcessingOutboxMapper outboxMapper;

    @Mock
    private MediaProcessingPublisher publisher;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MediaProcessingOutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        outboxPublisher = new MediaProcessingOutboxPublisher(
                outboxMapper,
                publisher,
                new MediaProcessingProperties(3, Duration.ofSeconds(5), Duration.ofMinutes(1), 20),
                objectMapper,
                transactionManager,
                new PlatformMetrics(new SimpleMeterRegistry())
        );
    }

    @Test
    void successfulPublishMarksRowPublished() {
        MediaProcessingOutbox row = row();
        outboxPublisher.publishOne(row);

        verify(publisher).publishProcessingRequested(any(MediaProcessingRequestedEvent.class));
        verify(outboxMapper).markPublished(eq(row.getId()), any(Instant.class));
    }

    @Test
    void publishFailureRemainsRetryable() {
        MediaProcessingOutbox row = row();
        doThrow(new IllegalStateException("broker down")).when(publisher).publishProcessingRequested(any());

        outboxPublisher.publishOne(row);

        ArgumentCaptor<Instant> nextAttempt = ArgumentCaptor.forClass(Instant.class);
        verify(outboxMapper).markRetry(eq(row.getId()), nextAttempt.capture(), eq("broker down"), any(Instant.class));
        assertThat(nextAttempt.getValue()).isAfter(Instant.now().minusSeconds(1));
    }

    @Test
    void backoffIsBounded() {
        assertThat(MediaProcessingOutboxPublisher.backoff(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(MediaProcessingOutboxPublisher.backoff(8)).isEqualTo(Duration.ofSeconds(300));
    }

    private MediaProcessingOutbox row() {
        MediaProcessingRequestedEvent event = MediaProcessingRequestedEvent.create(42L, "a".repeat(64), "raw/" + "a".repeat(64));
        MediaProcessingOutbox row = new MediaProcessingOutbox();
        row.setId(11L);
        row.setMediaObjectId(42L);
        row.setPayload(objectMapper.writeValueAsString(event));
        row.setAttemptCount(1);
        return row;
    }
}
