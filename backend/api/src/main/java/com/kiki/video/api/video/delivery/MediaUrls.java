package com.kiki.video.api.video.delivery;

public final class MediaUrls {

    private MediaUrls() {
    }

    public static String proxyMasterPlaylist(long videoId) {
        return "/api/videos/" + videoId + "/hls/master.m3u8";
    }

    public static String proxyContent(long videoId) {
        return "/api/videos/" + videoId + "/content";
    }

    public static String proxyThumbnail(long videoId) {
        return "/api/videos/" + videoId + "/thumbnail";
    }
}
