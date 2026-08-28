package com.kiki.video.api.upload;

import java.util.UUID;

public final class UploadObjectKeys {

    private UploadObjectKeys() {
    }

    public static String chunk(UUID uploadId, int chunkIndex) {
        return "uploads/" + uploadId + "/chunks/" + chunkIndex;
    }

    public static String chunkPrefix(UUID uploadId) {
        return "uploads/" + uploadId + "/chunks/";
    }

    public static String raw(String sha256) {
        return "raw/" + sha256;
    }
}
