package com.kiki.video.api.search.dto;

import java.util.List;

public record SearchHighlights(
        List<HighlightSpan> title,
        List<HighlightSpan> description,
        List<HighlightSpan> ownerUsername,
        List<HighlightSpan> ownerDisplayName
) {
}
