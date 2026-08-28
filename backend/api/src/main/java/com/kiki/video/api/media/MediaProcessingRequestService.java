package com.kiki.video.api.media;

import com.kiki.video.api.config.MediaProcessingProperties;
import com.kiki.video.api.media.mapper.MediaProcessingOutboxMapper;
import com.kiki.video.api.media.model.MediaProcessingOutbox;
import com.kiki.video.api.media.model.OutboxStatus;
import com.kiki.video.api.upload.mapper.MediaObjectMapper;
import com.kiki.video.api.upload.model.MediaObject;
import com.kiki.video.common.media.MediaProcessingRequestedEvent;
import com.kiki.video.common.media.MediaProcessingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

@Service
public class MediaProcessingRequestService {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingRequestService.class);

    private final MediaObjectMapper mediaObjectMapper;
    private final MediaProcessingOutboxMapper outboxMapper;
    private final MediaProcessingProperties properties;
    private final ObjectMapper objectMapper;

    public MediaProcessingRequestService(
            MediaObjectMapper mediaObjectMapper,
            MediaProcessingOutboxMapper outboxMapper,
            MediaProcessingProperties properties,
            ObjectMapper objectMapper
    ) {
        this.mediaObjectMapper = mediaObjectMapper;
        this.outboxMapper = outboxMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean requestIfNeeded(MediaObject media) {
        if (media == null || media.getId() == null) {
            return false;
        }
        MediaObject current = mediaObjectMapper.findById(media.getId());
        if (current == null) {
            return false;
        }
        MediaProcessingStatus status = current.getProcessingStatus();
        if (status == MediaProcessingStatus.READY || status == MediaProcessingStatus.PROCESSING) {
            log.info(
                    "media processing requested skipped mediaObjectId={} status={}",
                    current.getId(),
                    status
            );
            return false;
        }
        if (status == MediaProcessingStatus.FAILED
                && current.getProcessingAttempts() >= properties.maxAttempts()) {
            log.info(
                    "media processing requested skipped mediaObjectId={} exhaustedAttempts={}",
                    current.getId(),
                    current.getProcessingAttempts()
            );
            return false;
        }
        if (status == MediaProcessingStatus.NOT_REQUESTED || status == MediaProcessingStatus.FAILED) {
            mediaObjectMapper.markPendingIfEligible(current.getId(), properties.maxAttempts(), Instant.now());
            current = mediaObjectMapper.findById(current.getId());
            if (current == null || current.getProcessingStatus() != MediaProcessingStatus.PENDING) {
                return false;
            }
        }
        return insertOutbox(current);
    }

    private boolean insertOutbox(MediaObject media) {
        if (outboxMapper.findActiveByMediaObjectId(media.getId()) != null) {
            return false;
        }
        Instant now = Instant.now();
        MediaProcessingRequestedEvent event = MediaProcessingRequestedEvent.create(
                media.getId(),
                media.getSha256(),
                media.getObjectKey()
        );
        MediaProcessingOutbox row = new MediaProcessingOutbox();
        row.setMediaObjectId(media.getId());
        row.setEventType(MediaProcessingRequestedEvent.EVENT_TYPE);
        row.setEventVersion(event.eventVersion());
        row.setPayload(objectMapper.writeValueAsString(event));
        row.setStatus(OutboxStatus.PENDING);
        row.setAttemptCount(0);
        row.setNextAttemptAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            outboxMapper.insert(row);
            log.info(
                    "media processing requested mediaObjectId={} sha256={} outboxId={}",
                    media.getId(),
                    media.getSha256(),
                    row.getId()
            );
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}
