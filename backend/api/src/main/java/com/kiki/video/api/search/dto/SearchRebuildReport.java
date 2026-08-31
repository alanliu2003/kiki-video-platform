package com.kiki.video.api.search.dto;

public record SearchRebuildReport(
        String indexName,
        String alias,
        long eligible,
        int indexed,
        int failed,
        long durationMs
) {
}
