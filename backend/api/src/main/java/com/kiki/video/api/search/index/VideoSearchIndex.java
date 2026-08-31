package com.kiki.video.api.search.index;

import java.util.List;

public interface VideoSearchIndex {

    void ensureWritableIndex();

    void upsert(VideoSearchDocument document);

    void delete(long videoId);

    BulkIndexResult bulkUpsert(String indexName, List<VideoSearchDocument> documents);

    VideoSearchHits search(VideoSearchQuery query);

    void refresh();

    boolean isAvailable();

    long count();

    record BulkIndexResult(int indexed, int failed) {
    }
}
