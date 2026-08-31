package com.kiki.video.api.search.rebuild;

import com.kiki.video.api.config.ElasticsearchProperties;
import com.kiki.video.api.search.dto.SearchRebuildReport;
import com.kiki.video.api.search.service.SearchRebuildService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order
@ConditionalOnProperty(name = "app.search.rebuild", havingValue = "true")
public class SearchRebuildRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchRebuildRunner.class);

    private final SearchRebuildService searchRebuildService;
    private final ElasticsearchProperties elasticsearchProperties;

    public SearchRebuildRunner(
            SearchRebuildService searchRebuildService,
            ElasticsearchProperties elasticsearchProperties
    ) {
        this.searchRebuildService = searchRebuildService;
        this.elasticsearchProperties = elasticsearchProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!elasticsearchProperties.enabled()) {
            throw new IllegalStateException("Search rebuild requires ELASTICSEARCH_ENABLED=true");
        }
        SearchRebuildReport report = searchRebuildService.rebuild();
        log.info(
                "search rebuild report indexed={} failed={} eligible={} durationMs={} index={}",
                report.indexed(),
                report.failed(),
                report.eligible(),
                report.durationMs(),
                report.indexName()
        );
    }
}
