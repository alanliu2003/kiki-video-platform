package com.kiki.video.api.search.index;

import com.kiki.video.api.config.ElasticsearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class VideoSearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VideoSearchIndexInitializer.class);

    private final VideoSearchIndex videoSearchIndex;
    private final ElasticsearchProperties properties;

    public VideoSearchIndexInitializer(VideoSearchIndex videoSearchIndex, ElasticsearchProperties properties) {
        this.videoSearchIndex = videoSearchIndex;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            videoSearchIndex.ensureWritableIndex();
            log.info(
                    "search index ready alias={} version={}",
                    properties.videoIndexAlias(),
                    properties.videoIndexVersion()
            );
        } catch (RuntimeException ex) {
            log.warn(
                    "search index initialization deferred; API will keep serving non-search traffic: {}",
                    ex.getMessage()
            );
        }
    }
}
