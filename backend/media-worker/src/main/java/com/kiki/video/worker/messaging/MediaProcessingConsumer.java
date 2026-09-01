package com.kiki.video.worker.messaging;

import com.kiki.video.common.media.MediaProcessingRequestedEvent;
import com.kiki.video.worker.config.WorkerRocketMqProperties;
import com.kiki.video.worker.observability.WorkerMetrics;
import com.kiki.video.worker.processing.MediaProcessingService;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "app.rocketmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MediaProcessingConsumer {

    private static final Logger log = LoggerFactory.getLogger(MediaProcessingConsumer.class);

    private final WorkerRocketMqProperties properties;
    private final MediaProcessingService processingService;
    private final ObjectMapper objectMapper;
    private final WorkerMetrics metrics;
    private final DefaultMQPushConsumer consumer;

    public MediaProcessingConsumer(
            WorkerRocketMqProperties properties,
            MediaProcessingService processingService,
            ObjectMapper objectMapper,
            WorkerMetrics metrics
    ) {
        this.properties = properties;
        this.processingService = processingService;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.consumer = new DefaultMQPushConsumer(properties.consumerGroup());
        this.consumer.setNamesrvAddr(properties.namesrvAddr());
        this.consumer.setVipChannelEnabled(false);
    }

    @PostConstruct
    public void start() throws Exception {
        consumer.subscribe(properties.mediaTopic(), "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
            for (MessageExt message : messages) {
                try {
                    String body = new String(message.getBody(), StandardCharsets.UTF_8);
                    MediaProcessingRequestedEvent event = objectMapper.readValue(
                            body,
                            MediaProcessingRequestedEvent.class
                    );
                    metrics.jobConsumed();
                    log.info(
                            "media processing event received mediaObjectId={} msgId={}",
                            event.mediaObjectId(),
                            message.getMsgId()
                    );
                    processingService.handle(event);
                } catch (RuntimeException ex) {
                    log.warn("media processing event failed msgId={}", message.getMsgId(), ex);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info(
                "RocketMQ media processing consumer started namesrv={} topic={} group={}",
                properties.namesrvAddr(),
                properties.mediaTopic(),
                properties.consumerGroup()
        );
    }

    @PreDestroy
    public void stop() {
        consumer.shutdown();
    }
}
