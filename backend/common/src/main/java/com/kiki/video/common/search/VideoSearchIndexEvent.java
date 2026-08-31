package com.kiki.video.common.search;

public record VideoSearchIndexEvent(
        int eventVersion,
        long videoId
) {

    public static final int CURRENT_VERSION = 1;
    public static final String UPSERT = "VIDEO_SEARCH_UPSERT";
    public static final String DELETE = "VIDEO_SEARCH_DELETE";

    public static VideoSearchIndexEvent of(long videoId) {
        return new VideoSearchIndexEvent(CURRENT_VERSION, videoId);
    }
}
