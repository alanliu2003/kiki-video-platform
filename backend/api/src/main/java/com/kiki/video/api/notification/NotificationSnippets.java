package com.kiki.video.api.notification;

public final class NotificationSnippets {

    public static final int MAX_LENGTH = 120;

    private NotificationSnippets() {
    }

    public static String snippet(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int codePoints = trimmed.codePointCount(0, trimmed.length());
        if (codePoints <= MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_LENGTH));
    }
}
