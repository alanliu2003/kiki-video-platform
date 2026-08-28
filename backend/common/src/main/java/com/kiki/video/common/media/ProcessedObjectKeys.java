package com.kiki.video.common.media;

public final class ProcessedObjectKeys {

    private ProcessedObjectKeys() {
    }

    public static String prefix(long mediaObjectId) {
        return "processed/" + mediaObjectId + "/";
    }

    public static String stagingPrefix(long mediaObjectId) {
        return prefix(mediaObjectId) + "staging/";
    }

    public static String master(long mediaObjectId) {
        return prefix(mediaObjectId) + "master.m3u8";
    }

    public static String stagingMaster(long mediaObjectId) {
        return stagingPrefix(mediaObjectId) + "master.m3u8";
    }

    public static String thumbnail(long mediaObjectId) {
        return prefix(mediaObjectId) + "thumbnail.jpg";
    }

    public static String stagingThumbnail(long mediaObjectId) {
        return stagingPrefix(mediaObjectId) + "thumbnail.jpg";
    }

    public static String renditionPlaylist(long mediaObjectId, String renditionName) {
        return prefix(mediaObjectId) + renditionName + "/index.m3u8";
    }

    public static String stagingRenditionPrefix(long mediaObjectId, String renditionName) {
        return stagingPrefix(mediaObjectId) + renditionName + "/";
    }

    public static String finalKey(long mediaObjectId, String stagingKey) {
        String staging = stagingPrefix(mediaObjectId);
        if (!stagingKey.startsWith(staging)) {
            throw new IllegalArgumentException("Object key is not inside the staging prefix");
        }
        return prefix(mediaObjectId) + stagingKey.substring(staging.length());
    }
}
