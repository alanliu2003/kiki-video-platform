package com.kiki.video.api.search.highlight;

import com.kiki.video.api.search.dto.HighlightSpan;

import java.util.ArrayList;
import java.util.List;

public final class HighlightParser {

    public static final String PRE = "[[HIGHLIGHT]]";
    public static final String POST = "[[/HIGHLIGHT]]";

    private HighlightParser() {
    }

    public static List<HighlightSpan> parse(String fragment) {
        List<HighlightSpan> spans = new ArrayList<>();
        if (fragment == null || fragment.isEmpty()) {
            return List.of();
        }
        int cursor = 0;
        while (cursor < fragment.length()) {
            int start = fragment.indexOf(PRE, cursor);
            if (start < 0) {
                add(spans, fragment.substring(cursor), false);
                break;
            }
            if (start > cursor) {
                add(spans, fragment.substring(cursor, start), false);
            }
            int contentStart = start + PRE.length();
            int end = fragment.indexOf(POST, contentStart);
            if (end < 0) {
                add(spans, fragment.substring(start), false);
                break;
            }
            add(spans, fragment.substring(contentStart, end), true);
            cursor = end + POST.length();
        }
        return List.copyOf(spans);
    }

    public static String plainText(List<HighlightSpan> spans) {
        StringBuilder text = new StringBuilder();
        for (HighlightSpan span : spans) {
            text.append(span.text());
        }
        return text.toString();
    }

    private static void add(List<HighlightSpan> spans, String text, boolean highlighted) {
        if (text == null || text.isEmpty()) {
            return;
        }
        spans.add(new HighlightSpan(text, highlighted));
    }
}
