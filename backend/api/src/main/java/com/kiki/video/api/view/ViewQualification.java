package com.kiki.video.api.view;

public final class ViewQualification {

    static final long MAX_WATCHED_MS = 24L * 60 * 60 * 1000;

    private ViewQualification() {
    }

    public static long thresholdMs(Long durationMs, double qualifySeconds, double qualifyPercent) {
        long qualifyMs = Math.max(1, Math.round(qualifySeconds * 1000.0));
        if (durationMs == null || durationMs <= 0) {
            return qualifyMs;
        }
        long percentMs = Math.max(1, Math.round(durationMs * qualifyPercent));
        return Math.min(qualifyMs, percentMs);
    }

    public static boolean meets(long watchedMs, Long durationMs, double qualifySeconds, double qualifyPercent) {
        return isWatchedMsUsable(watchedMs)
                && watchedMs >= thresholdMs(durationMs, qualifySeconds, qualifyPercent);
    }

    public static Long resolveDurationMs(Double authoritativeSeconds, Long clientDurationMs) {
        if (authoritativeSeconds != null && Double.isFinite(authoritativeSeconds) && authoritativeSeconds > 0) {
            return Math.max(1L, Math.round(authoritativeSeconds * 1000.0));
        }
        if (clientDurationMs != null && clientDurationMs > 0 && clientDurationMs <= MAX_WATCHED_MS) {
            return clientDurationMs;
        }
        return null;
    }

    public static boolean isWatchedMsUsable(long watchedMs) {
        return watchedMs >= 0 && watchedMs <= MAX_WATCHED_MS;
    }
}
