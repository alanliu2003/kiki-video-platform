package com.kiki.video.api.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.elasticsearch.enabled=true")
public abstract class AbstractSearchIntegrationTest extends AbstractIntegrationTest {

    static {
        ElasticsearchTestContainer.ELASTICSEARCH.start();
    }

    @DynamicPropertySource
    static void registerSearchProperties(DynamicPropertyRegistry registry) {
        registry.add("app.elasticsearch.url", ElasticsearchTestContainer::url);
        registry.add("app.search.outbox-poll-interval", () -> "1h");
        registry.add("app.search.rebuild", () -> "false");
    }
}
