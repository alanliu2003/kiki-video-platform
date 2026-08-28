package com.kiki.video.api.danmaku.realtime;

import com.kiki.video.api.config.DanmakuProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class DanmakuRedisConfig {

    @Bean
    public RedisMessageListenerContainer danmakuRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            DanmakuRedisSubscriber subscriber,
            DanmakuProperties properties
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(properties.redisChannel()));
        container.setRecoveryInterval(5000);
        container.setErrorHandler(throwable ->
                org.slf4j.LoggerFactory.getLogger(DanmakuRedisConfig.class)
                        .warn("Danmaku Redis subscriber error; will retry", throwable));
        return container;
    }
}
