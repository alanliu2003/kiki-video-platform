package com.kiki.video.api.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSnippetsTest {

    @Test
    void returnsNullForBlankContent() {
        assertThat(NotificationSnippets.snippet(null)).isNull();
        assertThat(NotificationSnippets.snippet("   ")).isNull();
    }

    @Test
    void keepsShortContent() {
        assertThat(NotificationSnippets.snippet("  Nice clip  ")).isEqualTo("Nice clip");
    }

    @Test
    void truncatesLongContentToMaxCodePoints() {
        String content = "a".repeat(NotificationSnippets.MAX_LENGTH + 25);
        String snippet = NotificationSnippets.snippet(content);
        assertThat(snippet).hasSize(NotificationSnippets.MAX_LENGTH);
        assertThat(snippet).isEqualTo("a".repeat(NotificationSnippets.MAX_LENGTH));
    }
}
