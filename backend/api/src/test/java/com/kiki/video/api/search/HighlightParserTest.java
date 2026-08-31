package com.kiki.video.api.search;

import com.kiki.video.api.search.dto.HighlightSpan;
import com.kiki.video.api.search.highlight.HighlightParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HighlightParserTest {

    @Test
    void parsesCustomMarkersIntoSafeSpans() {
        List<HighlightSpan> spans = HighlightParser.parse("GTA [[HIGHLIGHT]]Trailer[[/HIGHLIGHT]] night");

        assertThat(spans).containsExactly(
                new HighlightSpan("GTA ", false),
                new HighlightSpan("Trailer", true),
                new HighlightSpan(" night", false)
        );
        assertThat(HighlightParser.plainText(spans)).isEqualTo("GTA Trailer night");
    }

    @Test
    void treatsUnclosedMarkerAsPlainText() {
        assertThat(HighlightParser.parse("hello [[HIGHLIGHT]]oops"))
                .containsExactly(
                        new HighlightSpan("hello ", false),
                        new HighlightSpan("[[HIGHLIGHT]]oops", false)
                );
    }
}
