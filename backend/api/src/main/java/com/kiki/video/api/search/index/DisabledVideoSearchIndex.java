package com.kiki.video.api.search.index;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledVideoSearchIndex implements VideoSearchIndex {

    @Override
    public void ensureWritableIndex() {
        throw unavailable();
    }

    @Override
    public void upsert(VideoSearchDocument document) {
        throw unavailable();
    }

    @Override
    public void delete(long videoId) {
        throw unavailable();
    }

    @Override
    public BulkIndexResult bulkUpsert(String indexName, List<VideoSearchDocument> documents) {
        throw unavailable();
    }

    @Override
    public VideoSearchHits search(VideoSearchQuery query) {
        throw unavailable();
    }

    @Override
    public void refresh() {
        throw unavailable();
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public long count() {
        throw unavailable();
    }

    private static SearchIndexException unavailable() {
        return new SearchIndexException("Elasticsearch is disabled");
    }
}
