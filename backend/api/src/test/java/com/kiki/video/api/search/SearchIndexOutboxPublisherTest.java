package com.kiki.video.api.search.outbox;

import com.kiki.video.api.config.ElasticsearchProperties;
import com.kiki.video.api.config.SearchProperties;
import com.kiki.video.api.search.index.SearchIndexException;
import com.kiki.video.api.search.index.VideoSearchDocument;
import com.kiki.video.api.search.index.VideoSearchIndex;
import com.kiki.video.api.search.mapper.SearchIndexOutboxMapper;
import com.kiki.video.api.search.mapper.SearchVideoMapper;
import com.kiki.video.api.search.model.SearchIndexOutbox;
import com.kiki.video.api.search.model.SearchOutboxStatus;
import com.kiki.video.api.search.model.SearchVideoRow;
import com.kiki.video.api.search.outbox.SearchIndexOutboxPublisher;
import com.kiki.video.common.search.VideoSearchIndexEvent;
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
class SearchIndexOutboxPublisherTest {

    @Mock
    private SearchIndexOutboxMapper outboxMapper;

    @Mock
    private SearchVideoMapper searchVideoMapper;

    @Mock
    private VideoSearchIndex videoSearchIndex;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SearchIndexOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        publisher = new SearchIndexOutboxPublisher(
                outboxMapper,
                searchVideoMapper,
                videoSearchIndex,
                new SearchProperties(Duration.ofSeconds(5), Duration.ofMinutes(1), 20, 250, false),
                new ElasticsearchProperties(true, "http://127.0.0.1:9200", "kiki-videos", "kiki-videos-v1"),
                objectMapper,
                transactionManager
        );
    }

    @Test
    void successfulProjectMarksRowPublished() {
        SearchIndexOutbox row = row();
        SearchVideoRow video = new SearchVideoRow();
        video.setVideoId(9L);
        video.setTitle("Demo");
        video.setOwnerId(1L);
        video.setOwnerUsername("alice");
        video.setOwnerDisplayName("Alice");
        video.setStatus("UPLOADED");
        video.setProcessingStatus("PENDING");
        video.setCreatedAt(Instant.parse("2026-08-31T01:00:00Z"));
        video.setThumbnailAvailable(false);
        when(searchVideoMapper.findByVideoId(9L)).thenReturn(video);

        publisher.publishOne(row);

        verify(videoSearchIndex).upsert(any(VideoSearchDocument.class));
        verify(outboxMapper).markPublished(eq(row.getId()), any(Instant.class));
    }

    @Test
    void indexFailureRemainsRetryable() {
        SearchIndexOutbox row = row();
        SearchVideoRow video = new SearchVideoRow();
        video.setVideoId(9L);
        video.setTitle("Demo");
        video.setOwnerId(1L);
        video.setOwnerUsername("alice");
        video.setOwnerDisplayName("Alice");
        video.setStatus("UPLOADED");
        video.setProcessingStatus("PENDING");
        video.setCreatedAt(Instant.parse("2026-08-31T01:00:00Z"));
        when(searchVideoMapper.findByVideoId(9L)).thenReturn(video);
        doThrow(new SearchIndexException("cluster down")).when(videoSearchIndex).upsert(any());

        publisher.publishOne(row);

        ArgumentCaptor<Instant> nextAttempt = ArgumentCaptor.forClass(Instant.class);
        verify(outboxMapper).markRetry(eq(row.getId()), nextAttempt.capture(), eq("cluster down"), any(Instant.class));
        assertThat(nextAttempt.getValue()).isAfter(Instant.now().minusSeconds(1));
    }

    @Test
    void backoffIsBounded() {
        assertThat(SearchIndexOutboxPublisher.backoff(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(SearchIndexOutboxPublisher.backoff(8)).isEqualTo(Duration.ofSeconds(300));
    }

    private SearchIndexOutbox row() {
        SearchIndexOutbox row = new SearchIndexOutbox();
        row.setId(3L);
        row.setVideoId(9L);
        row.setEventType(VideoSearchIndexEvent.UPSERT);
        row.setEventVersion(1);
        row.setPayload(objectMapper.writeValueAsString(VideoSearchIndexEvent.of(9L)));
        row.setStatus(SearchOutboxStatus.PUBLISHING);
        row.setAttemptCount(1);
        return row;
    }
}
