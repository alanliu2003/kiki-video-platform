package com.kiki.video.api.observability.health;

import com.kiki.video.api.config.RocketMqProperties;
import com.kiki.video.common.observability.NamesrvProbe;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("rocketmq")
public class RocketMqHealthIndicator implements HealthIndicator {

    private final RocketMqProperties properties;

    public RocketMqHealthIndicator(RocketMqProperties properties) {
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.enabled()) {
            return DependencyHealth.disabled("rocketmq");
        }
        if (NamesrvProbe.reachable(properties.namesrvAddr(), 200)) {
            return DependencyHealth.up("rocketmq");
        }
        return DependencyHealth.degraded("rocketmq", "namesrv unreachable: " + properties.namesrvAddr());
    }
}
