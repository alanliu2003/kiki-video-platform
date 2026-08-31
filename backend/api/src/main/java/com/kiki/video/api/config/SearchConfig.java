package com.kiki.video.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ElasticsearchProperties.class, SearchProperties.class})
public class SearchConfig {
}
