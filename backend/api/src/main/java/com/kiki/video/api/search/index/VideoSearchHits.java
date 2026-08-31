package com.kiki.video.api.search.index;

import java.util.List;
import java.util.Map;

public record VideoSearchHits(
        List<Hit> items,
        long total,
        long tookMs
) {

    public record Hit(
            VideoSearchDocument document,
            Map<String, List<String>> highlightFragments
    ) {
    }
}
