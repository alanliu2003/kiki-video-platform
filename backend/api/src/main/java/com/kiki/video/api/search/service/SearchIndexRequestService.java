package com.kiki.video.api.search.service;

import com.kiki.video.api.search.mapper.SearchIndexOutboxMapper;
import com.kiki.video.api.search.model.SearchIndexOutbox;
import com.kiki.video.api.search.model.SearchOutboxStatus;
import com.kiki.video.common.search.VideoSearchIndexEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Service
public class SearchIndexRequestService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexRequestService.class);

    private final SearchIndexOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public SearchIndexRequestService(SearchIndexOutboxMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    public void enqueueUpsert(Long videoId) {
        if (videoId == null) {
            return;
        }
        if (outboxMapper.findActiveUpsert(videoId) != null) {
            return;
        }
        Instant now = Instant.now();
        VideoSearchIndexEvent event = VideoSearchIndexEvent.of(videoId);
        SearchIndexOutbox row = new SearchIndexOutbox();
        row.setVideoId(videoId);
        row.setEventType(VideoSearchIndexEvent.UPSERT);
        row.setEventVersion(event.eventVersion());
        row.setPayload(objectMapper.writeValueAsString(event));
        row.setStatus(SearchOutboxStatus.PENDING);
        row.setAttemptCount(0);
        row.setNextAttemptAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            outboxMapper.insert(row);
            log.info("search index upsert requested videoId={} outboxId={}", videoId, row.getId());
        } catch (DataIntegrityViolationException ex) {
            log.debug("search index upsert already pending videoId={}", videoId);
        }
    }
}
