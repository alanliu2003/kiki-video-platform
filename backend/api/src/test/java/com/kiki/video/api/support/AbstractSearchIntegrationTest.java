package com.kiki.video.api.support;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import com.kiki.video.api.config.ElasticsearchProperties;
import com.kiki.video.api.search.index.VideoSearchIndex;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;

@TestPropertySource(properties = {
        "app.elasticsearch.enabled=true",
        "app.elasticsearch.video-index-alias=kiki-videos-it",
        "app.elasticsearch.video-index-version=kiki-videos-it-v1"
})
public abstract class AbstractSearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private ElasticsearchProperties elasticsearchProperties;

    @Autowired
    private VideoSearchIndex videoSearchIndex;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    static {
        ElasticsearchTestContainer.ELASTICSEARCH.start();
    }

    @DynamicPropertySource
    static void registerSearchProperties(DynamicPropertyRegistry registry) {
        registry.add("app.elasticsearch.url", ElasticsearchTestContainer::url);
        registry.add("app.search.outbox-poll-interval", () -> "1h");
        registry.add("app.search.rebuild", () -> "false");
    }

    @BeforeEach
    void isolateSearchIndexAndOutbox() throws IOException {
        jdbcTemplate.update("DELETE FROM search_index_outbox");
        videoSearchIndex.ensureWritableIndex();
        waitForSearchIndex();
        if (videoSearchIndex.count() == 0) {
            return;
        }
        clearSearchDocuments();
    }

    private void waitForSearchIndex() throws IOException {
        String alias = elasticsearchProperties.videoIndexAlias();
        if (!elasticsearchClient.indices().existsAlias(e -> e.name(alias)).value()) {
            return;
        }
        elasticsearchClient.cluster().health(h -> h
                .index(alias)
                .waitForStatus(HealthStatus.Yellow)
                .timeout(t -> t.time("30s"))
        );
    }

    private void clearSearchDocuments() throws IOException {
        String alias = elasticsearchProperties.videoIndexAlias();
        elasticsearchClient.deleteByQuery(d -> d
                .index(alias)
                .query(q -> q.matchAll(m -> m))
                .refresh(true)
        );
    }
}
