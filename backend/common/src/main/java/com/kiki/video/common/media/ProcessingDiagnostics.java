package com.kiki.video.common.media;

public final class ProcessingDiagnostics {

    public static final int MAX_ERROR_LENGTH = 500;

    private ProcessingDiagnostics() {
    }

    public static String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Processing failed";
        }
        String sanitized = message.replace('\n', ' ').replace('\r', ' ').strip();
        if (sanitized.length() <= MAX_ERROR_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_ERROR_LENGTH);
    }
}
