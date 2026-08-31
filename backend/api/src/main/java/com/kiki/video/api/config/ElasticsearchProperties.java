package com.kiki.video.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.elasticsearch")
public record ElasticsearchProperties(
        boolean enabled,
        String url,
        String videoIndexAlias,
        String videoIndexVersion
) {
}
