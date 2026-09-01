package com.kiki.video.api.observability.health;

import com.kiki.video.api.config.ElasticsearchProperties;
import com.kiki.video.api.config.RocketMqProperties;
import com.kiki.video.api.search.index.VideoSearchIndex;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DependencyHealthIndicatorTest {

    @Test
    void elasticsearchDisabledIsUp() {
        ElasticsearchHealthIndicator indicator = new ElasticsearchHealthIndicator(
                new ElasticsearchProperties(false, "http://127.0.0.1:9200", "kiki-videos", "kiki-videos-v1"),
                mock(VideoSearchIndex.class)
        );
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("enabled", false);
    }

    @Test
    void elasticsearchUnavailableIsDegradedNotDown() {
        VideoSearchIndex index = mock(VideoSearchIndex.class);
        when(index.isAvailable()).thenReturn(false);
        ElasticsearchHealthIndicator indicator = new ElasticsearchHealthIndicator(
                new ElasticsearchProperties(true, "http://127.0.0.1:9200", "kiki-videos", "kiki-videos-v1"),
                index
        );
        assertThat(indicator.health().getStatus()).isEqualTo(DependencyHealth.DEGRADED);
        assertThat(indicator.health().getStatus()).isNotEqualTo(Status.DOWN);
    }

    @Test
    void rocketmqDisabledIsUp() {
        RocketMqHealthIndicator indicator = new RocketMqHealthIndicator(
                new RocketMqProperties(false, "127.0.0.1:9876", "media-processing", "api", "worker")
        );
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }
}
