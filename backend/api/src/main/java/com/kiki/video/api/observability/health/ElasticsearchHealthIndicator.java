package com.kiki.video.api.observability.health;

import com.kiki.video.api.config.ElasticsearchProperties;
import com.kiki.video.api.search.index.VideoSearchIndex;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("elasticsearch")
public class ElasticsearchHealthIndicator implements HealthIndicator {

    private final ElasticsearchProperties properties;
    private final VideoSearchIndex videoSearchIndex;

    public ElasticsearchHealthIndicator(
            ElasticsearchProperties properties,
            VideoSearchIndex videoSearchIndex
    ) {
        this.properties = properties;
        this.videoSearchIndex = videoSearchIndex;
    }

    @Override
    public Health health() {
        if (!properties.enabled()) {
            return DependencyHealth.disabled("elasticsearch");
        }
        try {
            if (videoSearchIndex.isAvailable()) {
                return DependencyHealth.up("elasticsearch");
            }
            return DependencyHealth.degraded("elasticsearch", "cluster ping failed");
        } catch (RuntimeException ex) {
            return DependencyHealth.degraded("elasticsearch", ex.getMessage());
        }
    }
}
