package com.kiki.video.api.media;

import com.kiki.video.api.config.RocketMqProperties;
import com.kiki.video.common.media.MediaProcessingRequestedEvent;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "app.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RocketMqMediaProcessingPublisher implements MediaProcessingPublisher, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RocketMqMediaProcessingPublisher.class);

    private final RocketMqProperties properties;
    private final ObjectMapper objectMapper;
    private final DefaultMQProducer producer;
    private volatile boolean started;

    public RocketMqMediaProcessingPublisher(RocketMqProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.producer = new DefaultMQProducer(properties.producerGroup());
        this.producer.setNamesrvAddr(properties.namesrvAddr());
        this.producer.setVipChannelEnabled(false);
    }

    @Override
    public void publishProcessingRequested(MediaProcessingRequestedEvent event) {
        try {
            ensureStarted();
            byte[] body = objectMapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8);
            Message message = new Message(
                    properties.mediaTopic(),
                    MediaProcessingRequestedEvent.EVENT_TYPE,
                    String.valueOf(event.mediaObjectId()),
                    body
            );
            producer.send(message);
            log.info(
                    "media processing requested published mediaObjectId={} topic={}",
                    event.mediaObjectId(),
                    properties.mediaTopic()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to publish media processing request", ex);
        }
    }

    private synchronized void ensureStarted() throws MQClientException {
        if (started) {
            return;
        }
        producer.start();
        started = true;
        log.info("RocketMQ media processing producer started namesrv={}", properties.namesrvAddr());
    }

    @Override
    public void close() {
        if (started) {
            producer.shutdown();
        }
    }
}
