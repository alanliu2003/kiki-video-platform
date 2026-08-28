package com.kiki.video.api.media;

import com.kiki.video.common.media.MediaProcessingRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.rocketmq", name = "enabled", havingValue = "false")
public class NoOpMediaProcessingPublisher implements MediaProcessingPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpMediaProcessingPublisher.class);

    @Override
    public void publishProcessingRequested(MediaProcessingRequestedEvent event) {
        log.debug(
                "RocketMQ disabled; skipping publish of media processing request mediaObjectId={}",
                event.mediaObjectId()
        );
    }
}
